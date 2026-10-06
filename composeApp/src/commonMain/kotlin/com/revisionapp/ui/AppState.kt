package com.revisionapp.ui

import com.revisionapp.data.sync.ContentSync
import com.revisionapp.data.sync.SyncError
import com.revisionapp.data.sync.SyncReport
import com.revisionapp.data.sync.SyncResult
import com.revisionapp.di.AppGraph
import com.revisionapp.di.SettingKeys
import com.revisionapp.domain.check.DefaultAnswerChecker
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.Ids
import com.revisionapp.domain.model.InstalledPack
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.Tag
import com.revisionapp.domain.model.TagGroup
import com.revisionapp.domain.model.TagId
import com.revisionapp.domain.model.TagMatch
import com.revisionapp.domain.model.Topic
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.model.randomUuid
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.QuestionFactory
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.domain.usecase.StreakCalculator
import com.revisionapp.ui.render.RichTextRenderer
import com.revisionapp.ui.session.SessionCard
import com.revisionapp.ui.session.SessionEvent
import com.revisionapp.ui.session.SessionState
import com.revisionapp.ui.session.StudySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Destinations. Hand-rolled rather than a navigation library: see DECISIONS.md D6. */
sealed interface Route {
    data object Browse : Route
    data object Study : Route
    data object Stats : Route
    data object Settings : Route

    /** Editing an existing card, or creating a new one when [cardId] is null. */
    data class EditCard(val cardId: CardId?, val presetTopicId: TopicId? = null) : Route

    /** Editing an existing topic, or creating a new one when [topicId] is null. */
    data class EditTopic(val topicId: TopicId?, val presetParentId: TopicId? = null) : Route

    /** Editing an existing tag, or creating a new one when [tagId] is null. */
    data class EditTag(val tagId: TagId?) : Route
}

/** What the settings screen shows about content syncing. */
sealed interface SyncUiState {
    data object Never : SyncUiState
    data object Running : SyncUiState
    data class Done(val report: SyncReport, val at: Instant) : SyncUiState
    data class Failed(val error: SyncError, val at: Instant) : SyncUiState
}

/** One row of the per-topic accuracy table. */
data class TopicAccuracyRow(
    val topicId: TopicId,
    val topicName: String,
    val reviews: Int,
    val correct: Int,
    val accuracy: Double,
)

/**
 * Settings, loaded once and kept in a flow. Reading the database from inside
 * composition would block the Android main thread, so screens never call the
 * store directly.
 */
data class SettingsUi(
    val baseUrl: String,
    val desiredRetention: Double,
    val packs: List<InstalledPack>,
) {
    companion object {
        fun initial(): SettingsUi = SettingsUi(ContentSync.DEFAULT_BASE_URL, DEFAULT_RETENTION, emptyList())

        const val DEFAULT_RETENTION: Double = 0.9
        const val MIN_RETENTION: Double = 0.7
        const val MAX_RETENTION: Double = 0.97
    }
}

data class StatsSnapshot(
    val dueToday: Int,
    val dueNow: Int,
    val streakDays: Int,
    val daysStudied: Int,
    val totalReviews: Int,
    val totalCards: Int,
    val userCards: Int,
    val accuracy: List<TopicAccuracyRow>,
) {
    companion object {
        fun empty(): StatsSnapshot = StatsSnapshot(0, 0, 0, 0, 0, 0, 0, emptyList())
    }
}

/**
 * The one state holder every screen talks to. Unidirectional: screens read the
 * flows and call the intent methods below; nothing else writes to them.
 *
 * All repository and network work happens on [Dispatchers.Default] so that the
 * Android entry point never blocks its main thread.
 */
class AppState(
    private val graph: AppGraph,
    private val scope: CoroutineScope,
) {
    private val _route = MutableStateFlow<Route>(Route.Browse)
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _snapshot = MutableStateFlow(LibrarySnapshot.Empty)
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _filter = MutableStateFlow(CardFilter.None)
    val filter: StateFlow<CardFilter> = _filter.asStateFlow()

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Never)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _stats = MutableStateFlow(StatsSnapshot.empty())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Empty)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _settingsUi = MutableStateFlow(SettingsUi.initial())
    val settingsUi: StateFlow<SettingsUi> = _settingsUi.asStateFlow()

    private val backStack = ArrayDeque<Route>()
    private var session: StudySession? = null

    val platformName: String get() = graph.platformName
    val dataDirectory: String get() = graph.dataDirectory

    /** Screens render card text through this; never touch raw LaTeX directly. */
    val renderer: RichTextRenderer get() = graph.renderer

    fun contentBaseUrl(): String = graph.contentBaseUrl()

    /** First load: the library, then the stats that depend on it. */
    fun start() {
        restoreSyncState()
        refresh()
        loadSettings()
    }

    fun refresh() {
        scope.launch(Dispatchers.Default) {
            val snapshot = graph.planner.snapshot(graph.clock.now())
            _snapshot.value = snapshot
            loadStatsInto(snapshot)
        }
    }

    // ---------------------------------------------------------- navigation ---

    fun navigate(route: Route) {
        backStack.addLast(_route.value)
        _route.value = route
    }

    /**
     * Switches between the four top-level tabs. Unlike [navigate] this does not
     * grow the back stack, so Back from an editor still returns to the tab the
     * editor was opened from rather than to a tab visited on the way.
     */
    fun switchTab(route: Route) {
        if (_route.value == route) return
        backStack.removeAll { it in TopLevelTabs }
        _route.value = route
    }

    fun back() {
        val previous = backStack.removeLastOrNull()
        _route.value = previous ?: Route.Browse
        if (_route.value != Route.Study) session = null
    }

    // ------------------------------------------------------------- filters ---

    fun toggleTopic(topicId: TopicId) {
        _filter.value = _filter.value.let { current ->
            current.copy(topicIds = if (topicId in current.topicIds) current.topicIds - topicId else current.topicIds + topicId)
        }
    }

    /** Narrows the topic selection to exactly one topic, or clears it. */
    fun focusTopic(topicId: TopicId?) {
        _filter.value = _filter.value.copy(topicIds = if (topicId == null) emptySet() else setOf(topicId))
    }

    fun toggleTag(tagId: TagId) {
        _filter.value = _filter.value.let { current ->
            current.copy(tagIds = if (tagId in current.tagIds) current.tagIds - tagId else current.tagIds + tagId)
        }
    }

    fun setMatchAllTags(value: Boolean) {
        _filter.value = _filter.value.copy(
            tagMatch = if (value) TagMatch.ALL else TagMatch.ANY_WITHIN_GROUP,
        )
    }

    fun setDueOnly(value: Boolean) {
        _filter.value = _filter.value.copy(dueOnly = value)
    }

    fun setNewOnly(value: Boolean) {
        _filter.value = _filter.value.copy(newOnly = value)
    }

    fun setSource(source: ContentSource?) {
        _filter.value = _filter.value.copy(source = source)
    }

    fun clearFilters() {
        _filter.value = CardFilter.None
    }

    // --------------------------------------------------------------- study ---

    /**
     * Builds a session from the current filter. Cards the chosen mode cannot
     * present (a long paragraph in tile mode, a thin topic in MCQ) drop out
     * silently here.
     */
    fun startStudy(mode: StudyMode) {
        scope.launch(Dispatchers.Default) {
            val snapshot = _snapshot.value
            val filter = _filter.value
            val queue = snapshot.dueCards(filter)
                .sortedBy { snapshot.stateOf(it.id).dueAt }
                .take(StudySession.MAX_ITEMS)

            val checker = DefaultAnswerChecker(corpus = graph.library.corpus())
            val scheduler = graph.scheduler()
            val items = queue.mapNotNull { card -> buildSessionCard(card, mode, snapshot) }

            session = StudySession(
                items = items,
                checker = checker,
                scheduler = scheduler,
                clock = graph.clock,
                persistSchedule = { cardId, state -> graph.progress.saveState(cardId, state) },
                recordReview = { entry -> graph.progress.record(entry) },
            )
            _sessionState.value = session?.state ?: SessionState.Empty
            navigate(Route.Study)
        }
    }

    private fun buildSessionCard(card: Card, mode: StudyMode, snapshot: LibrarySnapshot): SessionCard? {
        val schedule = snapshot.stateOf(card.id)
        val siblings = snapshot.siblingsOf(card)
        val question: Question = QuestionFactory.create(
            card = card,
            mode = mode,
            siblings = siblings,
            modeEscalation = schedule.modeEscalation,
        ) ?: return null
        return SessionCard(card, modeOf(question), question, schedule)
    }

    private fun modeOf(question: Question): StudyMode = when (question) {
        is Question.Flashcard -> StudyMode.FLASHCARD
        is Question.Typed -> StudyMode.TYPED
        is Question.Tiles -> StudyMode.TILES
        is Question.MultipleChoice -> StudyMode.MCQ
    }

    fun onSessionEvent(event: SessionEvent) {
        val current = session ?: return
        current.onEvent(event)
        _sessionState.value = current.state
        if (current.state is SessionState.Finished) refresh()
    }

    fun endSession() {
        session = null
        _sessionState.value = SessionState.Empty
        backStack.removeAll { it == Route.Study }
        _route.value = Route.Browse
        refresh()
    }

    // -------------------------------------------------------------- authoring ---

    fun saveTopic(existingId: TopicId?, name: String, parentId: TopicId?) {
        scope.launch(Dispatchers.Default) {
            val id = existingId ?: Ids.userTopic(randomUuid())
            val existing = _snapshot.value.topics.firstOrNull { it.id == id }
            val sortOrder = existing?.sortOrder ?: (_snapshot.value.topics.size + 1)
            graph.library.saveTopic(
                Topic(
                    id = id,
                    name = name.trim(),
                    parentId = parentId,
                    sortOrder = sortOrder,
                    source = ContentSource.USER,
                    packId = null,
                ),
            )
            afterMutation()
        }
    }

    fun deleteTopic(topicId: TopicId) {
        scope.launch(Dispatchers.Default) {
            // Reparent rather than orphan: children move up to the deleted topic's parent.
            val snapshot = _snapshot.value
            val victim = snapshot.topics.firstOrNull { it.id == topicId }
            for (child in snapshot.topics.filter { it.parentId == topicId }) {
                graph.library.saveTopic(child.copy(parentId = victim?.parentId, source = ContentSource.USER))
            }
            graph.library.deleteTopic(topicId)
            afterMutation()
        }
    }

    fun saveTag(existingId: TagId?, name: String, group: String) {
        scope.launch(Dispatchers.Default) {
            val id = existingId ?: Ids.userTag(randomUuid())
            graph.library.saveTag(
                Tag(
                    id = id,
                    name = name.trim(),
                    group = TagGroup(group.trim().ifEmpty { TagGroup.Custom.value }),
                    source = ContentSource.USER,
                ),
            )
            afterMutation()
        }
    }

    fun deleteTag(tagId: TagId) {
        scope.launch(Dispatchers.Default) {
            graph.library.deleteTag(tagId)
            afterMutation()
        }
    }

    fun saveCard(card: Card) {
        scope.launch(Dispatchers.Default) {
            graph.library.saveCard(card)
            afterMutation()
        }
    }

    fun newCardId(): CardId = Ids.userCard(randomUuid())

    fun deleteCard(cardId: CardId) {
        scope.launch(Dispatchers.Default) {
            graph.library.deleteCard(cardId)
            afterMutation()
        }
    }

    /** Copies read-only built-in content into editable user content, then opens the copy. */
    fun duplicateCard(card: Card) {
        scope.launch(Dispatchers.Default) {
            val newId = Ids.userCard(randomUuid())
            graph.library.duplicateAsUser(card, newId, topicId = card.topicId)
            afterMutation()
            navigate(Route.EditCard(newId))
        }
    }

    private fun afterMutation() {
        _snapshot.value = graph.planner.snapshot(graph.clock.now())
        loadStatsInto(_snapshot.value)
    }

    // --------------------------------------------------------------- stats ---

    fun loadStats() {
        scope.launch(Dispatchers.Default) { loadStatsInto(graph.planner.snapshot(graph.clock.now())) }
    }

    private fun loadStatsInto(snapshot: LibrarySnapshot) {
        val zone = TimeZone.currentSystemDefault()
        val now = graph.clock.now()
        val today = now.toLocalDateTime(zone).date
        val endOfToday = today.atStartOfDayIn(zone) + 1.days
        val days = graph.progress.reviewDays()
        val names = snapshot.topics.associate { it.id to it.name }

        _stats.value = StatsSnapshot(
            dueToday = snapshot.dueByEndOfDay(endOfToday),
            dueNow = snapshot.cards.count { snapshot.isDue(it) },
            streakDays = StreakCalculator.currentStreak(days, today),
            daysStudied = StreakCalculator.daysStudied(days),
            totalReviews = graph.progress.reviewCount(),
            totalCards = snapshot.cards.size,
            userCards = snapshot.cards.count { it.source == ContentSource.USER },
            accuracy = graph.progress.accuracyByTopic()
                .map { (topicId, accuracy) ->
                    TopicAccuracyRow(
                        topicId = topicId,
                        topicName = names[topicId] ?: topicId.value,
                        reviews = accuracy.reviews,
                        correct = accuracy.correct,
                        accuracy = accuracy.accuracy,
                    )
                }
                .sortedByDescending { it.reviews },
        )
    }

    // ---------------------------------------------------------------- sync ---

    fun syncNow() {
        if (_syncState.value == SyncUiState.Running) return
        _syncState.value = SyncUiState.Running
        scope.launch(Dispatchers.Default) {
            val at = graph.clock.now()
            val result = runCatching { graph.contentSync().sync() }.getOrElse { error ->
                SyncResult.Fatal(SyncError.Network(error.message ?: error.toString()))
            }
            _syncState.value = when (result) {
                is SyncResult.Completed -> SyncUiState.Done(result.report, at)
                is SyncResult.Fatal -> SyncUiState.Failed(result.error, at)
            }
            rememberSync(result, at)
            _snapshot.value = graph.planner.snapshot(graph.clock.now())
            loadStatsInto(_snapshot.value)
            loadSettingsInto()
        }
    }

    private fun rememberSync(result: SyncResult, at: Instant) {
        graph.settings.write(SettingKeys.LAST_SYNC_AT, at.toEpochMilliseconds().toString())
        graph.settings.write(SettingKeys.LAST_SYNC_SUMMARY, describe(result))
    }

    private fun restoreSyncState() {
        val at = graph.settings.read(SettingKeys.LAST_SYNC_AT)?.toLongOrNull() ?: return
        val summary = graph.settings.read(SettingKeys.LAST_SYNC_SUMMARY) ?: return
        _syncState.value = if (summary.startsWith("Failed:")) {
            SyncUiState.Failed(SyncError.CorruptContent(summary.removePrefix("Failed:")), Instant.fromEpochMilliseconds(at))
        } else {
            SyncUiState.Done(
                SyncReport(updated = emptyList(), unchanged = emptyList(), errors = emptyList()),
                Instant.fromEpochMilliseconds(at),
            )
        }
    }

    private fun describe(result: SyncResult): String = when (result) {
        is SyncResult.Completed -> "Updated ${result.report.updated.size}, unchanged ${result.report.unchanged.size}, " +
            "errors ${result.report.errors.size}"

        is SyncResult.Fatal -> "Failed: ${result.error.describe()}"
    }

    fun setBaseUrl(url: String) {
        scope.launch(Dispatchers.Default) {
            val value = url.trim()
            graph.settings.write(
                SettingKeys.CONTENT_BASE_URL,
                value.ifBlank { ContentSync.DEFAULT_BASE_URL },
            )
            loadSettingsInto()
        }
    }

    fun setDesiredRetention(value: Double) {
        scope.launch(Dispatchers.Default) {
            val clamped = value.coerceIn(SettingsUi.MIN_RETENTION, SettingsUi.MAX_RETENTION)
            graph.settings.write(SettingKeys.DESIRED_RETENTION, clamped.toString())
            loadSettingsInto()
        }
    }

    fun loadSettings() {
        scope.launch(Dispatchers.Default) { loadSettingsInto() }
    }

    /** Blocking; only ever called from a `Dispatchers.Default` coroutine. */
    private fun loadSettingsInto() {
        _settingsUi.value = SettingsUi(
            baseUrl = graph.contentBaseUrl(),
            desiredRetention = graph.settings.read(SettingKeys.DESIRED_RETENTION)?.toDoubleOrNull()
                ?: SettingsUi.DEFAULT_RETENTION,
            packs = graph.packs.installed(),
        )
    }

    companion object {
        /** The four tabs. Editors are pushed on top of them, never beside them. */
        private val TopLevelTabs: Set<Route> = setOf(Route.Browse, Route.Study, Route.Stats, Route.Settings)
    }
}
