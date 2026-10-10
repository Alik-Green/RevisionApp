package com.revisionapp.ui

import com.revisionapp.data.content.CourseCatalogLoader
import com.revisionapp.data.sync.ContentSync
import com.revisionapp.data.sync.SyncError
import com.revisionapp.data.sync.SyncReport
import com.revisionapp.data.sync.SyncResult
import com.revisionapp.di.AppGraph
import com.revisionapp.di.SettingKeys
import com.revisionapp.domain.check.DefaultAnswerChecker
import com.revisionapp.domain.course.CourseCatalog
import com.revisionapp.domain.course.LearningCourse
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
import com.revisionapp.domain.progression.LearnerProgress
import com.revisionapp.domain.progression.ProgressionRules
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.QuestionFactory
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.domain.usecase.StreakCalculator
import com.revisionapp.domain.usecase.WeeklyQuestCalculator
import com.revisionapp.domain.usecase.WeeklyQuestProgress
import com.revisionapp.ui.render.RichTextRenderer
import com.revisionapp.ui.session.SessionCard
import com.revisionapp.ui.session.SessionEvent
import com.revisionapp.ui.session.SessionState
import com.revisionapp.ui.session.SessionSummary
import com.revisionapp.ui.session.StudySession
import com.revisionapp.ui.theme.ThemeMode
import com.revisionapp.ui.theme.ThemeStyle
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Destinations. Hand-rolled rather than a navigation library: see DECISIONS.md D6. */
sealed interface Route {
    data object Study : Route
    data object Progression : Route
    data object Profile : Route

    /** Existing card-library screens are reachable from Profile, not the tab bar. */
    data object Library : Route

    /** Kept as an alias so older internal links land on the new progression hub. */
    data object Stats : Route
    data object Settings : Route
    data object LegacyStudy : Route
    data class Lesson(val courseId: String, val lessonId: String) : Route

    /** Editing an existing card, or creating a new one when [cardId] is null. */
    data class EditCard(val cardId: CardId?, val presetTopicId: TopicId? = null) : Route

    /** Editing an existing topic, or creating a new one when [topicId] is null. */
    data class EditTopic(val topicId: TopicId?, val presetParentId: TopicId? = null) : Route

    /** Editing an existing tag, or creating a new one when [tagId] is null. */
    data class EditTag(val tagId: TagId?) : Route

    /** Debug screen under Settings > Developer: both maths renderers side by side. */
    data object MathGallery : Route
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
    val themeStyle: ThemeStyle = ThemeStyle.PLAYFUL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        fun initial(): SettingsUi =
            SettingsUi(
                ContentSync.DEFAULT_BASE_URL,
                DEFAULT_RETENTION,
                emptyList(),
                ThemeStyle.PLAYFUL,
                ThemeMode.SYSTEM,
            )

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
    val questPoints: Int = 0,
    val studiedDaysThisWeek: Int = 0,
    val weeklyQuests: List<WeeklyQuestProgress> = emptyList(),
) {
    companion object {
        fun empty(): StatsSnapshot = StatsSnapshot(
            dueToday = 0,
            dueNow = 0,
            streakDays = 0,
            daysStudied = 0,
            totalReviews = 0,
            totalCards = 0,
            userCards = 0,
            accuracy = emptyList(),
        )
    }
}

private data class SessionStreakContext(
    val alreadyStudiedToday: Boolean,
    val currentStreak: Int,
)

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
    private val _route = MutableStateFlow<Route>(Route.Study)
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

    private val _courseCatalog = MutableStateFlow(CourseCatalog(CourseCatalog.CURRENT_SCHEMA_VERSION, emptyList()))
    val courseCatalog: StateFlow<CourseCatalog> = _courseCatalog.asStateFlow()

    private val _courseCatalogLoading = MutableStateFlow(true)
    val courseCatalogLoading: StateFlow<Boolean> = _courseCatalogLoading.asStateFlow()

    private val _courseCatalogError = MutableStateFlow<String?>(null)
    val courseCatalogError: StateFlow<String?> = _courseCatalogError.asStateFlow()

    private val _learnerProgress = MutableStateFlow(LearnerProgress())
    val learnerProgress: StateFlow<LearnerProgress> = _learnerProgress.asStateFlow()

    private val _progressionReady = MutableStateFlow(false)
    val progressionReady: StateFlow<Boolean> = _progressionReady.asStateFlow()

    private val progressionWriteMutex = Mutex()

    /**
     * Where the user currently is in the topic tree; null is the Library root.
     *
     * The Library is an explorer, not a multi-select filter panel: there is always
     * exactly one location, everything on screen is relative to it, and the topic
     * half of [filter] is derived from it rather than chosen separately.
     */
    private val _location = MutableStateFlow<TopicId?>(null)
    val location: StateFlow<TopicId?> = _location.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** True once the user widens a search from the current location to everywhere. */
    private val _searchEverywhere = MutableStateFlow(false)
    val searchEverywhere: StateFlow<Boolean> = _searchEverywhere.asStateFlow()

    /** Cards per session. [SESSION_SIZE_ALL] is the "All" choice. */
    private val _sessionSize = MutableStateFlow(DEFAULT_SESSION_SIZE)
    val sessionSize: StateFlow<Int> = _sessionSize.asStateFlow()

    private val _settingsUi = MutableStateFlow(SettingsUi.initial())
    val settingsUi: StateFlow<SettingsUi> = _settingsUi.asStateFlow()

    private val backStack = ArrayDeque<Route>()
    private var session: StudySession? = null
    private var sessionStreakContext: SessionStreakContext? = null

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
        loadCourseCatalogAndProgression()
        syncOnFirstRun()
    }

    private fun loadCourseCatalogAndProgression() {
        scope.launch(Dispatchers.Default) {
            val catalog = runCatching { CourseCatalogLoader.load() }
                .onFailure { _courseCatalogError.value = it.message ?: "Could not load V2 course content" }
                .getOrNull()
            if (catalog != null) {
                _courseCatalog.value = catalog
                _courseCatalogError.value = null
            }
            _courseCatalogLoading.value = false

            val restored = graph.settings.read(SettingKeys.LEARNER_PROGRESS_V2)?.let { raw ->
                runCatching { graph.json.decodeFromString<LearnerProgress>(raw) }.getOrNull()
            } ?: LearnerProgress()
            val firstCourse = _courseCatalog.value.courses.firstOrNull()?.id
            val selectedCourseId = if (_courseCatalog.value.course(restored.activeCourseId) != null) {
                restored.activeCourseId
            } else {
                firstCourse ?: restored.activeCourseId
            }
            val today = localToday()
            val progress = ProgressionRules.forToday(
                restored.copy(activeCourseId = selectedCourseId),
                today,
            )
            _learnerProgress.value = progress
            graph.settings.write(SettingKeys.LEARNER_PROGRESS_V2, graph.json.encodeToString(progress))
            _progressionReady.value = true
        }
    }

    /**
     * Fetches the built-in packs when none are installed, so a fresh install has
     * content without the user having to find the settings screen, and so a first
     * sync that failed offline is retried on the next launch. Once a pack exists
     * the app never syncs on its own again: updates stay an explicit choice.
     */
    private fun syncOnFirstRun() {
        scope.launch(Dispatchers.Default) {
            if (graph.packs.installed().isEmpty()) syncNow()
        }
    }

    fun refresh() {
        scope.launch(Dispatchers.Default) {
            val snapshot = graph.planner.snapshot(graph.clock.now())
            _snapshot.value = snapshot
            // A sync or an edit can change which descendants the current location
            // has, so the scope has to be re-derived from the new tree.
            applyLocationToFilter()
            loadStatsInto(snapshot)
        }
    }

    // ---------------------------------------------------------- navigation ---

    fun navigate(route: Route) {
        backStack.addLast(_route.value)
        _route.value = route
    }

    /** Switches to a primary tab and discards any secondary route history. */
    fun switchTab(route: Route) {
        if (_route.value == route) return
        backStack.clear()
        _route.value = route
    }

    fun back() {
        val previous = backStack.removeLastOrNull()
        _route.value = previous ?: Route.Study
        if (_route.value != Route.LegacyStudy) session = null
    }

    // ---------------------------------------------------------- V2 courses ---

    fun canOpenCourseLesson(courseId: String, lessonId: String): Boolean {
        val course = _courseCatalog.value.course(courseId) ?: return false
        val ordered = course.orderedLessons()
        val index = ordered.indexOfFirst { it.id == lessonId }
        if (index < 0) return false
        return ordered.take(index).all { ProgressionRules.hasCompleted(_learnerProgress.value, courseId, it.id) }
    }

    fun isCourseLessonComplete(courseId: String, lessonId: String): Boolean =
        ProgressionRules.hasCompleted(_learnerProgress.value, courseId, lessonId)

    fun openCourseLesson(courseId: String, lessonId: String) {
        if (!_progressionReady.value || !canOpenCourseLesson(courseId, lessonId)) return
        navigate(Route.Lesson(courseId, lessonId))
    }

    fun selectActiveCourse(courseId: String) {
        if (_courseCatalog.value.course(courseId) == null) return
        updateProgression { ProgressionRules.setActiveCourse(it, courseId) }
    }

    fun setDisplayName(name: String) {
        updateProgression { ProgressionRules.setDisplayName(it, name) }
    }

    fun recordCourseQuestionAnswered() {
        updateProgression { ProgressionRules.answerQuestion(it, localToday()).progress }
    }

    fun completeCourseLesson(courseId: String, lessonId: String) {
        val course = _courseCatalog.value.course(courseId) ?: return
        if (course.lesson(lessonId) == null) return
        updateProgression { ProgressionRules.completeLesson(it, courseId, lessonId, localToday()).progress }
    }

    fun buyBackStreak() {
        updateProgression { ProgressionRules.buyBackStreak(it, localToday()).progress }
    }

    fun refreshProgressionForToday() {
        updateProgression { it }
    }

    private fun updateProgression(transform: (LearnerProgress) -> LearnerProgress) {
        if (!_progressionReady.value) return
        val today = localToday()
        _learnerProgress.value = transform(ProgressionRules.forToday(_learnerProgress.value, today))
        scope.launch(Dispatchers.Default) {
            progressionWriteMutex.withLock {
                graph.settings.write(
                    SettingKeys.LEARNER_PROGRESS_V2,
                    graph.json.encodeToString(_learnerProgress.value),
                )
            }
        }
    }

    private fun localToday(): LocalDate = graph.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // ------------------------------------------------------------- filters ---

    fun toggleTopic(topicId: TopicId) {
        _filter.value = _filter.value.let { current ->
            current.copy(topicIds = if (topicId in current.topicIds) current.topicIds - topicId else current.topicIds + topicId)
        }
    }

    /** Moves into [topicId], or out to the Library root when it is null. */
    fun openTopic(topicId: TopicId?) {
        _location.value = topicId
        applyLocationToFilter()
    }

    /** Up one level. From the root this does nothing. */
    fun navigateUp() {
        val current = _location.value ?: return
        openTopic(_snapshot.value.tree.find(current)?.topic?.parentId)
    }

    /** The breadcrumb path to the current location, root first. */
    fun locationPath(): List<TopicId> {
        val location = _location.value ?: return emptyList()
        return _snapshot.value.tree.breadcrumbs(location).map { it.id }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchEverywhere(everywhere: Boolean) {
        _searchEverywhere.value = everywhere
    }

    /**
     * Keeps the topic half of the filter in step with the location, including every
     * descendant, so a session started here studies the whole subtree.
     */
    private fun applyLocationToFilter() {
        val location = _location.value
        val tree = _snapshot.value.tree
        val ids = if (location == null) emptySet() else tree.selectedTopicIds(setOf(location))
        _filter.value = _filter.value.copy(topicIds = ids)
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

    /** How many cards the next session takes; [SESSION_SIZE_ALL] means no limit. */
    private fun sessionLimit(): Int {
        val size = _sessionSize.value
        return if (size <= 0) Int.MAX_VALUE else size
    }

    fun setSessionSize(size: Int) {
        _sessionSize.value = size
    }

    /**
     * Starts a lesson without asking the learner to choose a question mode. Due
     * cards are preferred; when none are due, the scoped cards can be studied ahead.
     */
    fun startStudy() {
        scope.launch(Dispatchers.Default) {
            val snapshot = _snapshot.value
            val filter = _filter.value
            val due = snapshot.dueCards(filter)
            val queue = (if (due.isNotEmpty()) due else snapshot.filtered(filter))
                .sortedBy { snapshot.stateOf(it.id).dueAt }
                .take(sessionLimit())

            val zone = TimeZone.currentSystemDefault()
            val today = graph.clock.now().toLocalDateTime(zone).date
            val reviewDays = graph.progress.reviewDays()
            sessionStreakContext = SessionStreakContext(
                alreadyStudiedToday = today.toString() in reviewDays,
                currentStreak = StreakCalculator.currentStreak(reviewDays, today),
            )

            val checker = DefaultAnswerChecker(corpus = graph.library.corpus())
            val scheduler = graph.scheduler()
            val items = queue.mapNotNull { card -> buildSessionCard(card, StudyMode.MIXED, snapshot) }
            val learned = graph.learned.all()

            session = StudySession(
                items = items,
                checker = checker,
                scheduler = scheduler,
                clock = graph.clock,
                // Session events arrive from the UI thread, so every write is handed
                // to a background dispatcher: answering a card must not do disk I/O
                // on the thread that is animating the feedback.
                persistSchedule = { cardId, state ->
                    scope.launch(Dispatchers.Default) { graph.progress.saveState(cardId, state) }
                },
                recordReview = { entry ->
                    scope.launch(Dispatchers.Default) { graph.progress.record(entry) }
                },
                learned = learned,
                onLearn = { cardId, answer ->
                    scope.launch(Dispatchers.Default) { graph.learned.add(cardId, answer) }
                },
            )
            _sessionState.value = session?.state ?: SessionState.Empty
            navigate(Route.LegacyStudy)
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
        val finished = current.state as? SessionState.Finished
        if (finished == null) {
            _sessionState.value = current.state
        } else {
            val context = sessionStreakContext ?: SessionStreakContext(false, 0)
            val streakDays = when {
                context.alreadyStudiedToday -> context.currentStreak
                context.currentStreak > 0 -> context.currentStreak + 1
                else -> 1
            }
            val summary = finished.summary.copy(
                streakDays = streakDays,
                streakAdvanced = !context.alreadyStudiedToday,
            )
            _sessionState.value = SessionState.Finished(summary)
            refreshAfterSession(summary)
        }
    }

    /** Refresh after the session and optimistically include today's completed review. */
    private fun refreshAfterSession(summary: SessionSummary) {
        scope.launch(Dispatchers.Default) {
            val snapshot = graph.planner.snapshot(graph.clock.now())
            _snapshot.value = snapshot
            applyLocationToFilter()
            val today = graph.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val days = graph.progress.reviewDays()
            val completedDays = if (summary.reviewed > 0) days + today.toString() else days
            loadStatsInto(snapshot, completedDays)
        }
    }

    fun endSession() {
        session = null
        sessionStreakContext = null
        _sessionState.value = SessionState.Empty
        backStack.clear()
        _route.value = Route.Library
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

    private fun loadStatsInto(snapshot: LibrarySnapshot, knownReviewDays: Collection<String>? = null) {
        val zone = TimeZone.currentSystemDefault()
        val now = graph.clock.now()
        val today = now.toLocalDateTime(zone).date
        val endOfToday = today.atStartOfDayIn(zone) + 1.days
        val days = (knownReviewDays ?: graph.progress.reviewDays()).distinct()
        val quests = WeeklyQuestCalculator.calculate(days, today)
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
            questPoints = quests.totalPoints,
            studiedDaysThisWeek = quests.studiedDaysThisWeek,
            weeklyQuests = quests.quests,
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
        is SyncResult.Completed ->
            "Updated ${result.report.updated.size}, unchanged ${result.report.unchanged.size}, " +
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

    /** Stores the selected colour style; the playful palette is the default. */
    fun setThemeStyle(style: ThemeStyle) {
        scope.launch(Dispatchers.Default) {
            graph.settings.write(SettingKeys.THEME_STYLE, style.name)
            loadSettingsInto()
        }
    }

    /** Light, dark or follow the system. Applied by `RevisionAppTheme` at the root. */
    fun setThemeMode(mode: ThemeMode) {
        scope.launch(Dispatchers.Default) {
            graph.settings.write(SettingKeys.THEME_MODE, mode.name)
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
            themeStyle = ThemeStyle.fromStored(graph.settings.read(SettingKeys.THEME_STYLE)),
            themeMode = ThemeMode.fromStored(graph.settings.read(SettingKeys.THEME_MODE)),
        )
    }

    companion object {
        /** The session-size choices the Study setup screen offers; 0 means "All". */
        const val SESSION_SIZE_ALL: Int = 0
        val SessionSizes: List<Int> = listOf(10, 20, 50, SESSION_SIZE_ALL)
        const val DEFAULT_SESSION_SIZE: Int = 20

        fun sessionSizeLabel(size: Int): String =
            if (size == SESSION_SIZE_ALL) "All" else size.toString()
    }
}
