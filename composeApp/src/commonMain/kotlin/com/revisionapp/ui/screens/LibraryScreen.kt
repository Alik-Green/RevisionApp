package com.revisionapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardFilter
import com.revisionapp.domain.model.TagMatch
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.model.TopicNode
import com.revisionapp.domain.usecase.LibrarySearch
import com.revisionapp.domain.usecase.LibrarySnapshot
import com.revisionapp.domain.usecase.ScopedFilters
import com.revisionapp.domain.usecase.ScopedTag
import com.revisionapp.domain.usecase.SearchSection
import com.revisionapp.domain.usecase.TagGroupOptions
import com.revisionapp.ui.AppState
import com.revisionapp.ui.FeatureFlags
import com.revisionapp.ui.Route
import com.revisionapp.ui.components.AppButton as Button
import com.revisionapp.ui.components.AppTextButton as TextButton
import com.revisionapp.ui.components.MathText
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip
import com.revisionapp.ui.nav.WindowSizeClass
import com.revisionapp.ui.theme.SubjectAccents
import com.revisionapp.ui.theme.appCornerShape
import com.revisionapp.ui.theme.appHeadingWeight
import com.revisionapp.ui.theme.appInset

/**
 * The Library: a file explorer over the topic tree, not a filter panel.
 *
 * The user is always in exactly one location, named in large type with a
 * breadcrumb above it, and everything on screen — the folders, the cards, the tag
 * filters and their counts, and the primary "Study this" action — is relative to
 * that location. Filters persist while navigating deeper and stay visible as
 * removable chips, so going down a level never silently changes what is being
 * looked at.
 */
@Composable
fun LibraryScreen(state: AppState) {
    val snapshot = state.snapshot.collectAsState().value
    val filter = state.filter.collectAsState().value
    val query = state.searchQuery.collectAsState().value
    val filtersOpen = remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = WindowSizeClass.fromWidth(maxWidth.value).usesBottomBar
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                LibraryTopBar(state, onFilters = { filtersOpen.value = !filtersOpen.value })
                if (query.isBlank()) {
                    LocationView(state, snapshot, filter)
                } else {
                    SearchResults(state, snapshot, filter, query)
                }
            }
            // On a wide window the filters sit beside the list, which is what a
            // popover is for; on a phone the same content arrives as a sheet.
            if (!compact && filtersOpen.value) {
                VerticalDivider(Modifier.fillMaxHeight())
                Column(
                    Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(appInset(16.dp)),
                ) {
                    FilterPanel(state, snapshot, filter)
                }
            }
        }
        if (compact && filtersOpen.value) {
            FilterSheet(state, snapshot, filter, onClose = { filtersOpen.value = false })
        }
    }
}

// ------------------------------------------------------------------ top bar ---

@Composable
private fun LibraryTopBar(state: AppState, onFilters: () -> Unit) {
    val query = state.searchQuery.collectAsState().value
    val filter = state.filter.collectAsState().value
    val snapshot = state.snapshot.collectAsState().value
    val location = state.location.collectAsState().value
    val searchOpen = remember { mutableStateOf(query.isNotBlank()) }
    val addOpen = remember { mutableStateOf(false) }
    val activeCount = filter.tagIds.size +
        (if (filter.dueOnly) 1 else 0) +
        (if (filter.newOnly) 1 else 0) +
        (if (filter.source != null) 1 else 0)

    val node = location?.let { snapshot.tree.find(it) }
    val children = if (node == null) snapshot.tree.roots else node.children
    val inSection = remember(snapshot, filter) { snapshot.filtered(filter).size }

    Column(Modifier.fillMaxWidth().padding(horizontal = appInset(16.dp), vertical = appInset(8.dp))) {
        // One compact row: where you are on the left, three small actions on the
        // right. The search field used to sit here permanently and dominated the
        // screen; it is now behind its icon, and New card / topic / tag live in one
        // overflow menu instead of a full-size plus.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
            Column(Modifier.weight(1f)) {
                BreadcrumbRow(state, snapshot)
                Text(
                    node?.name ?: "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    countLine(children.size, inSection, snapshot.dueCount(filter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                val opening = !searchOpen.value
                searchOpen.value = opening
                if (!opening) state.setSearchQuery("")
            }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = if (searchOpen.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onFilters) {
                Box {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filters",
                        tint = if (activeCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (activeCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, appCornerShape(4.dp)),
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { addOpen.value = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Add or create",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = addOpen.value, onDismissRequest = { addOpen.value = false }) {
                    DropdownMenuItem(
                        text = { Text("New card") },
                        leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                        onClick = {
                            addOpen.value = false
                            state.navigate(Route.EditCard(null, location))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New topic") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            addOpen.value = false
                            state.navigate(Route.EditTopic(null, location))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New tag") },
                        leadingIcon = { Icon(Icons.Filled.Style, contentDescription = null) },
                        onClick = {
                            addOpen.value = false
                            state.navigate(Route.EditTag(null))
                        },
                    )
                }
            }
        }
        if (searchOpen.value) {
            OutlinedTextField(
                value = query,
                onValueChange = { state.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(top = appInset(6.dp)),
                singleLine = true,
                placeholder = { Text("Search topics, tags and cards") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        state.setSearchQuery("")
                        searchOpen.value = false
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                },
            )
        }
        ActiveFilterRow(state, snapshot, filter)
    }
}

@Composable
private fun BreadcrumbRow(state: AppState, snapshot: LibrarySnapshot) {
    val location = state.location.collectAsState().value
    val crumbs = if (location == null) emptyList() else snapshot.tree.breadcrumbs(location)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appInset(3.dp)),
    ) {
        Text(
            "Library",
            style = MaterialTheme.typography.labelMedium,
            color = if (location == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.clickable(enabled = location != null) { state.openTopic(null) },
        )
        for (crumb in crumbs) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val isLast = crumb.id == location
            Text(
                crumb.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isLast) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.clickable(enabled = !isLast) { state.openTopic(crumb.id) },
            )
        }
    }
}

/** The filters currently narrowing this location, each removable in one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterRow(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter) {
    if (filter.isUnfiltered) return
    FlowRow(
        Modifier.fillMaxWidth().padding(top = appInset(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(appInset(6.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(6.dp)),
    ) {
        for (tagId in filter.tagIds) {
            val tag = snapshot.tags.firstOrNull { it.id == tagId }
            ToggleChip(
                label = (tag?.name ?: tagId.value) + "  \u00D7",
                selected = true,
                onClick = { state.toggleTag(tagId) },
            )
        }
        if (filter.dueOnly) {
            ToggleChip("Due only  \u00D7", selected = true, onClick = { state.setDueOnly(false) })
        }
        if (filter.newOnly) {
            ToggleChip("New only  \u00D7", selected = true, onClick = { state.setNewOnly(false) })
        }
        val source = filter.source
        if (source != null) {
            ToggleChip(
                label = source.name.lowercase() + "  \u00D7",
                selected = true,
                onClick = { state.setSource(null) },
            )
        }
        if (filter.tagMatch == TagMatch.ALL) {
            ToggleChip("Match all tags", selected = true, onClick = { state.setMatchAllTags(false) })
        }
        TextButton(onClick = { state.clearFilters() }) {
            Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Clear all")
        }
    }
}

// --------------------------------------------------------------- the list ---

@Composable
private fun LocationView(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter) {
    val location = state.location.collectAsState().value
    val node = location?.let { snapshot.tree.find(it) }
    // At the Library root there is no node to take children from: the roots of the
    // tree ARE the contents. Reading node?.children here listed nothing at all, so
    // a synced library of 201 cards reported itself empty.
    val children = if (node == null) snapshot.tree.roots else node.children
    val rootTopicIds = remember(snapshot) { snapshot.tree.roots.map { it.id }.toSet() }
    val cardsHere = remember(snapshot, location, filter, rootTopicIds) {
        val scoped = snapshot.filtered(filter)
        if (location == null) {
            scoped.filter { it.topicId in rootTopicIds }
        } else {
            scoped.filter { it.topicId == location }
        }
    }
    val scoped = remember(snapshot, filter) { snapshot.filtered(filter) }
    val dueCount = snapshot.dueCount(filter)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = appInset(16.dp), vertical = appInset(8.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(6.dp)),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                LibraryTabs()
                Button(onClick = { state.switchTab(Route.Study) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Study this (" + scoped.size.toString() + " cards, " + dueCount.toString() + " due)")
                }
                if (children.isNotEmpty() || cardsHere.isNotEmpty()) {
                    HorizontalDivider()
                }
            }
        }

        items(children, key = { "topic:" + it.id.value }) { child ->
            FolderRow(state, snapshot, child)
        }

        items(cardsHere, key = { "card:" + it.id.value }) { card ->
            LibraryCardRow(state, snapshot, card)
        }

        if (children.isEmpty() && cardsHere.isEmpty()) {
            item(key = "empty") { LibraryEmptyState(state, node, scoped.isEmpty()) }
        }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

/** `Cards | Notes`, with Notes disabled until the notebook model exists. */
@Composable
private fun LibraryTabs() {
    Row(horizontalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
        ToggleChip("Cards", selected = true, onClick = {})
        ToggleChip(
            label = "Notes",
            selected = false,
            onClick = {},
            enabled = FeatureFlags.NOTES_ENABLED,
        )
        if (!FeatureFlags.NOTES_ENABLED) {
            Text(
                "coming later",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun FolderRow(state: AppState, snapshot: LibrarySnapshot, node: TopicNode) {
    val accent = accentFor(snapshot, node)
    val menuOpen = remember(node.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, appCornerShape(12.dp))
            .clickable { state.openTopic(node.id) }
            .padding(horizontal = appInset(12.dp), vertical = appInset(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appInset(12.dp)),
    ) {
        Box(
            Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), appCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            MathText(
                node.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                node.cardCount.toString() + " card" + plural(node.cardCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (node.dueCount > 0) {
            DuePill(node.dueCount)
        }
        Box {
            IconButton(onClick = { menuOpen.value = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for " + node.name)
            }
            DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                DropdownMenuItem(
                    text = { Text("Study this topic") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = {
                        menuOpen.value = false
                        state.openTopic(node.id)
                        state.switchTab(Route.Study)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add card") },
                    leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                    onClick = {
                        menuOpen.value = false
                        state.navigate(Route.EditCard(null, node.id))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add subtopic") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        menuOpen.value = false
                        state.navigate(Route.EditTopic(null, node.id))
                    },
                )
                if (node.isEditable) {
                    DropdownMenuItem(
                        text = { Text("Rename or move") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen.value = false
                            state.navigate(Route.EditTopic(node.id))
                        },
                    )
                }
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryCardRow(state: AppState, snapshot: LibrarySnapshot, card: Card) {
    val menuOpen = remember(card.id) { mutableStateOf(false) }
    val tagNames = snapshot.tags.filter { it.id in card.tagIds }.joinToString("  ·  ") { it.name }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { state.navigate(Route.EditCard(card.id)) }
            .padding(horizontal = appInset(4.dp), vertical = appInset(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appInset(2.dp))) {
            MathText(
                card.front,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
                if (snapshot.isNew(card)) {
                    StatusPill("New", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                } else if (snapshot.isDue(card)) {
                    StatusPill("Due", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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
        Box {
            IconButton(onClick = { menuOpen.value = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions for this card")
            }
            DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                DropdownMenuItem(
                    text = { Text(if (card.isEditable) "Edit card" else "View card") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen.value = false
                        state.navigate(Route.EditCard(card.id))
                    },
                )
                if (!card.isEditable) {
                    DropdownMenuItem(
                        text = { Text("Duplicate to my content") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen.value = false
                            state.duplicateCard(card)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(state: AppState, node: TopicNode?, nothingMatchesFilter: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = appInset(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(appInset(10.dp)),
    ) {
        Icon(
            if (nothingMatchesFilter) Icons.Filled.FilterList else Icons.Filled.Notes,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when {
                nothingMatchesFilter -> "No cards match the filters here"
                node == null -> "Your library is empty"
                else -> "No cards in this topic yet"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            when {
                nothingMatchesFilter -> "Clear a filter, or widen the search to everywhere."
                node == null -> "Sync the built-in packs in Settings, or create your own topic."
                else -> "Add a card, or a subtopic to organise this one."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
            if (nothingMatchesFilter) {
                OutlinedActionButton("Clear filters", Icons.Filled.ClearAll) { state.clearFilters() }
            } else {
                OutlinedActionButton("Add a card", Icons.Filled.Add) {
                    state.navigate(Route.EditCard(null, node?.id))
                }
                OutlinedActionButton("New topic", Icons.Filled.CreateNewFolder) {
                    state.navigate(Route.EditTopic(null, node?.id))
                }
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

// --------------------------------------------------------------- filters ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: AppState,
    snapshot: LibrarySnapshot,
    filter: CardFilter,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = appInset(16.dp)),
            verticalArrangement = Arrangement.spacedBy(appInset(10.dp)),
        ) {
            FilterPanel(state, snapshot, filter)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Only the tags that occur inside the current location are offered, each with what
 * choosing it would return, so a chip that would empty the list is visibly dead
 * rather than a surprise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter) {
    val groups = remember(snapshot, filter) { ScopedFilters.tagGroups(snapshot, filter) }

    SectionLabel("Filters")
    Row(
        Modifier.fillMaxWidth().padding(vertical = appInset(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appInset(8.dp)),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Match all tags", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (filter.tagMatch == TagMatch.ALL) "Every selected tag must apply" else "Any tag within a group",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = filter.tagMatch == TagMatch.ALL,
            onCheckedChange = { state.setMatchAllTags(it) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
        ToggleChip("Due only", filter.dueOnly, { state.setDueOnly(!filter.dueOnly) })
        ToggleChip("New only", filter.newOnly, { state.setNewOnly(!filter.newOnly) })
    }

    if (groups.isEmpty()) {
        Text(
            "No tags apply in this location.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    for (group in groups) {
        TagGroupRow(state, group)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroupRow(state: AppState, group: TagGroupOptions) {
    Column(Modifier.fillMaxWidth().padding(vertical = appInset(4.dp)), verticalArrangement = Arrangement.spacedBy(appInset(4.dp))) {
        Text(
            group.group.value.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(appInset(6.dp)), verticalArrangement = Arrangement.spacedBy(appInset(6.dp))) {
            for (option in group.tags) {
                ScopedChip(state, option)
            }
        }
    }
}

@Composable
private fun ScopedChip(state: AppState, option: ScopedTag) {
    val count = if (option.isSelected) option.resultCount else option.availableCount
    ToggleChip(
        label = option.tag.name + "  " + count.toString(),
        selected = option.isSelected,
        onClick = { state.toggleTag(option.tag.id) },
        enabled = option.isSelected || option.wouldGiveResults,
    )
}

// ---------------------------------------------------------------- search ---

@Composable
private fun SearchResults(state: AppState, snapshot: LibrarySnapshot, filter: CardFilter, query: String) {
    val everywhere = state.searchEverywhere.collectAsState().value
    val location = state.location.collectAsState().value
    val scope = if (everywhere) CardFilter.None else filter
    val sections = remember(snapshot, query, scope) { LibrarySearch.search(snapshot, query, scope) }
    val locationName = location?.let { snapshot.tree.find(it)?.name }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = appInset(16.dp), vertical = appInset(8.dp)),
        verticalArrangement = Arrangement.spacedBy(appInset(6.dp)),
    ) {
        item(key = "scope") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(appInset(8.dp))) {
                Text(
                    if (everywhere) "Searching everywhere" else "Searching within: " + (locationName ?: "Library"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!everywhere && locationName != null) {
                    TextButton(onClick = { state.setSearchEverywhere(true) }) { Text("Widen") }
                } else if (everywhere && locationName != null) {
                    TextButton(onClick = { state.setSearchEverywhere(false) }) { Text("Narrow") }
                }
            }
        }
        if (sections.isEmpty()) {
            item(key = "none") {
                Text(
                    "Nothing matches \u201C" + query + "\u201D.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = appInset(24.dp)),
                )
            }
        }
        for (section in sections) {
            item(key = "section:" + section.title) {
                SectionLabel(section.title + "  (" + sectionSize(section) + ")")
            }
            when (section) {
                is SearchSection.Topics -> items(section.items, key = { "st:" + it.id.value }) { topic ->
                    val node = snapshot.tree.find(topic.id)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            state.openTopic(topic.id)
                            state.setSearchQuery("")
                        }.padding(vertical = appInset(10.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(appInset(10.dp)),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = accentFor(snapshot, topic.id))
                        Column(Modifier.weight(1f)) {
                            MathText(topic.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pathOf(snapshot, topic.id),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (node != null && node.dueCount > 0) DuePill(node.dueCount)
                    }
                }

                is SearchSection.Tags -> items(section.items, key = { "sg:" + it.id.value }) { tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            state.toggleTag(tag.id)
                            state.setSearchQuery("")
                        }.padding(vertical = appInset(10.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(appInset(10.dp)),
                    ) {
                        Icon(Icons.Filled.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                tag.group.value,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.Add, contentDescription = "Apply as a filter")
                    }
                }

                is SearchSection.Cards -> items(section.items, key = { "sc:" + it.id.value }) { card ->
                    Column(
                        Modifier.fillMaxWidth().clickable { state.navigate(Route.EditCard(card.id)) }
                            .padding(vertical = appInset(8.dp)),
                    ) {
                        MathText(
                            LibrarySearch.snippet(card.front, query),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pathOf(snapshot, card.topicId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

// ----------------------------------------------------------------- helpers ---

@Composable
private fun DuePill(count: Int) {
    StatusPill(
        label = count.toString() + " due",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
private fun StatusPill(label: String, container: Color, onContainer: Color) {
    Box(
        Modifier.background(container, appCornerShape(8.dp)).padding(horizontal = appInset(8.dp), vertical = appInset(3.dp)),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = onContainer, fontWeight = appHeadingWeight())
    }
}

/** The colour of the top-level subject this topic sits under. */
@Composable
private fun accentFor(snapshot: LibrarySnapshot, node: TopicNode): Color = accentFor(snapshot, node.id)

@Composable
private fun accentFor(snapshot: LibrarySnapshot, id: TopicId): Color {
    val root = snapshot.tree.pathTo(id).firstOrNull()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return SubjectAccents.forTopic(root?.name.orEmpty(), root?.id?.value ?: id.value, dark = isDark)
}

/** "Physics › Circular motion", for a search result's location line. */
private fun pathOf(snapshot: LibrarySnapshot, id: TopicId): String =
    snapshot.tree.breadcrumbs(id).joinToString("  \u203A  ") { it.name }

private fun sectionSize(section: SearchSection): Int = when (section) {
    is SearchSection.Topics -> section.items.size
    is SearchSection.Tags -> section.items.size
    is SearchSection.Cards -> section.items.size
}

/**
 * Counts only. The breadcrumb already gives the path and the title already gives
 * the name, so repeating "Inside Circular motion" here said the same thing twice.
 * "In this section" includes descendants, which is what studying from here covers.
 */
private fun countLine(subtopics: Int, inSection: Int, due: Int): String {
    val parts = ArrayList<String>(3)
    if (subtopics > 0) parts += subtopics.toString() + " subtopic" + plural(subtopics)
    parts += inSection.toString() + " card" + plural(inSection) + " in this section"
    parts += due.toString() + " due"
    return parts.joinToString("  \u00B7  ")
}

private fun plural(count: Int): String = if (count == 1) "" else "s"
