package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.ContentSource
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.TagMatch
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.model.TopicNode
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Above this width the filters sit beside the card list instead of above it. */
private val WideLayout = 760.dp

/** Chips per row; `FlowRow` is experimental, so wrapping is done by chunking. */
private const val CHIPS_PER_ROW = 3

/**
 * The library browser: topic tree with breadcrumbs and counts, tag filters, and
 * the cards the current filter selects. The same filter also drives study
 * sessions, which is why it lives in [AppState] rather than in this screen.
 */
@Composable
fun BrowseScreen(state: AppState) {
    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val cards = remember(snapshot, filter) { snapshot.filtered(filter) }
    val expanded = remember { mutableStateOf(setOf<TopicId>()) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Browse",
            subtitle = cards.size.toString() + " of " + snapshot.cards.size + " cards shown, " +
                snapshot.dueCount(filter) + " due",
            trailing = {
                TextButton(onClick = { state.navigate(Route.EditTopic(null)) }) { Text("New topic") }
                TextButton(onClick = { state.navigate(Route.EditTag(null)) }) { Text("New tag") }
                TextButton(onClick = { state.navigate(Route.EditCard(null)) }) { Text("New card") }
            },
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (maxWidth >= WideLayout) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        FiltersPanel(state, snapshot, filter, expanded.value) { id ->
                            expanded.value = toggle(expanded.value, id)
                        }
                    }
                    VerticalDivider(Modifier.fillMaxHeight())
                    CardColumn(state, snapshot, filter, cards, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            FiltersPanel(state, snapshot, filter, expanded.value) { id ->
                                expanded.value = toggle(expanded.value, id)
                            }
                            Spacer(Modifier.height(8.dp))
                            StudyBar(state, snapshot.dueCount(filter))
                        }
                    }
                    item { SectionLabel("Cards (" + cards.size + ")") }
                    items(cards, key = { it.id.value }) { card ->
                        CardRow(state, snapshot, card)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun toggle(current: Set<TopicId>, id: TopicId): Set<TopicId> =
    if (id in current) current - id else current + id

/** The tree expander glyph. A `when` rather than a nested if/else, which ktlint
 * would otherwise want wrapped in braces. */
private fun expanderLabel(isLeaf: Boolean, isOpen: Boolean): String = when {
    isLeaf -> " "
    isOpen -> "-"
    else -> "+"
}

/** Wide-screen right-hand pane: the study bar plus a lazy list of cards. */
@Composable
private fun CardColumn(
    state: AppState,
    snapshot: LibrarySnapshot,
    filter: CardFilter,
    cards: List<Card>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        StudyBar(state, snapshot.dueCount(filter))
        Spacer(Modifier.height(8.dp))
        SectionLabel("Cards (" + cards.size + ")")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(cards, key = { it.id.value }) { card ->
                CardRow(state, snapshot, card)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** One button per study mode; all of them use the filter currently on screen. */
@Composable
private fun StudyBar(state: AppState, dueCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Study - $dueCount due")
        for (row in StudyMode.All.chunked(CHIPS_PER_ROW)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in row) {
                    Box(Modifier.weight(1f)) {
                        ToggleChip(
                            label = mode.title,
                            selected = false,
                            onClick = { state.startStudy(mode) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersPanel(
    state: AppState,
    snapshot: LibrarySnapshot,
    filter: CardFilter,
    expanded: Set<TopicId>,
    onExpand: (TopicId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Breadcrumbs(state, snapshot, filter)

        SectionLabel("Topics")
        if (snapshot.tree.roots.isEmpty()) {
            EmptyMessage("No topics yet. Sync the built-in content packs, or create your own.")
        } else {
            TopicRows(state, filter, expanded, onExpand, snapshot.tree.roots)
        }

        HorizontalDivider()
        SectionLabel("Tags")
        if (snapshot.tags.isEmpty()) {
            EmptyMessage("No tags yet.")
        } else {
            TagFilters(state, snapshot, filter)
            ToggleChip(
                label = if (filter.tagMatch == TagMatch.ALL) "Match ALL tags" else "Any tag within a group",
                selected = filter.tagMatch == TagMatch.ALL,
                onClick = { state.setMatchAllTags(filter.tagMatch != TagMatch.ALL) },
            )
            EmptyMessage(
                "Within one group tags are OR-ed and groups are AND-ed, unless \"match all\" is on. " +
                    "Selecting a topic always includes its descendants.",
            )
        }

        HorizontalDivider()
        SectionLabel("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("Due only", filter.dueOnly, { state.setDueOnly(!filter.dueOnly) })
            ToggleChip("New only", filter.newOnly, { state.setNewOnly(!filter.newOnly) })
        }
        SectionLabel("Source")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("All", filter.source == null, { state.setSource(null) })
            ToggleChip("Built-in", filter.source == ContentSource.BUILTIN, { state.setSource(ContentSource.BUILTIN) })
            ToggleChip("Mine", filter.source == ContentSource.USER, { state.setSource(ContentSource.USER) })
        }
        if (!filter.isUnfiltered) {
            TextButton(onClick = { state.clearFilters() }) { Text("Clear all filters") }
        }
    }
}

/** Where the single focused topic sits in the tree. */
@Composable
private fun Breadcrumbs(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter) {
    val focus = filter.topicIds.singleOrNull()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = { state.focusTopic(null) }) { Text("All topics") }
        if (focus != null) {
            for (crumb in snapshot.tree.breadcrumbs(focus)) {
                Text(">", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { state.focusTopic(crumb.id) }) {
                    MathText(crumb.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TopicRows(
    state: AppState,
    filter: CardFilter,
    expanded: Set<TopicId>,
    onExpand: (TopicId) -> Unit,
    nodes: List<TopicNode>,
) {
    for (node in nodes) {
        val selected = node.id in filter.topicIds
        val isOpen = node.id in expanded
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (node.depth * 14).dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .clickable(enabled = !node.isLeaf) { onExpand(node.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(expanderLabel(node.isLeaf, isOpen), style = MaterialTheme.typography.labelLarge)
            }
            MathText(
                node.name,
                modifier = Modifier.weight(1f).clickable { state.toggleTopic(node.id) },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.cardCount.toString() + " / " + node.dueCount.toString() + " due",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { state.navigate(Route.EditTopic(null, node.id)) }) {
                Text("+", style = MaterialTheme.typography.labelLarge)
            }
            if (node.isEditable) {
                TextButton(onClick = { state.navigate(Route.EditTopic(node.id)) }) {
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (isOpen) {
            TopicRows(state, filter, expanded, onExpand, node.children)
        }
    }
}

@Composable
private fun TagFilters(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter) {
    val groups = snapshot.tags.groupBy { it.group.value }.toSortedMap()
    for ((groupName, tags) in groups) {
        SectionLabel(groupName)
        for (row in tags.chunked(CHIPS_PER_ROW)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in row) {
                    ToggleChip(
                        label = tag.name,
                        selected = tag.id in filter.tagIds,
                        onClick = { state.toggleTag(tag.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRow(state: AppState, snapshot: LibrarySnapshot, card: Card) {
    val topicName = snapshot.topics.firstOrNull { it.id == card.topicId }?.name ?: "unfiled"
    val schedule = snapshot.stateOf(card.id)
    val tagNames = snapshot.tags.filter { it.id in card.tagIds }.joinToString(", ") { it.name }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { state.navigate(Route.EditCard(card.id)) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MathText(
            card.front,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            topicName + "  -  " + card.answerType.name + "  -  " +
                if (card.source == ContentSource.BUILTIN) "built-in (read-only)" else "yours",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .background(dueColour(snapshot.isDue(card), schedule.isNew), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(dueLabel(snapshot, card), style = MaterialTheme.typography.labelSmall)
            }
            if (tagNames.isNotEmpty()) {
                Text(
                    tagNames,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun dueLabel(snapshot: LibrarySnapshot, card: Card): String {
    if (snapshot.isNew(card)) return "New"
    if (snapshot.isDue(card)) return "Due now"
    val zone = TimeZone.currentSystemDefault()
    return "Due " + snapshot.stateOf(card.id).dueAt.toLocalDateTime(zone).date.toString()
}

@Composable
private fun dueColour(isDue: Boolean, isNew: Boolean) =
    MaterialTheme.colorScheme.let { scheme ->
        when {
            isNew -> scheme.tertiaryContainer
            isDue -> scheme.secondaryContainer
            else -> scheme.surfaceVariant
        }
    }
