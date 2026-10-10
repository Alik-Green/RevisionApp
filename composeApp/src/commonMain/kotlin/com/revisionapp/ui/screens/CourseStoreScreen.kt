package com.revisionapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revisionapp.domain.course.CourseFileReference
import com.revisionapp.domain.course.LearningCourse
import com.revisionapp.domain.progression.AppearanceCategory
import com.revisionapp.domain.progression.AppearanceCatalog
import com.revisionapp.domain.progression.AppearanceItem
import com.revisionapp.ui.AppState
import com.revisionapp.ui.Route
import com.revisionapp.ui.StoreSection
import com.revisionapp.ui.components.AppHeader
import com.revisionapp.ui.components.CharacterPortrait
import com.revisionapp.ui.components.EmptyMessage
import com.revisionapp.ui.components.SectionLabel
import com.revisionapp.ui.components.ToggleChip

/** A single bottom-tab Store with separate course-download and cosmetics shelves. */
@Composable
fun CourseStoreScreen(state: AppState) {
    val selectedSection = state.storeSection.collectAsState().value
    LaunchedEffect(selectedSection) {
        if (selectedSection == StoreSection.COURSES) state.refreshCourseStore()
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Store",
            subtitle = "Courses to study · pieces to collect",
            trailing = {
                if (selectedSection == StoreSection.COURSES) {
                    val loading = state.courseStoreLoading.collectAsState().value
                    TextButton(onClick = { state.refreshCourseStore() }, enabled = !loading) {
                        Text(if (loading) "Loading…" else "Refresh")
                    }
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToggleChip(
                label = "📚 Courses",
                selected = selectedSection == StoreSection.COURSES,
                modifier = Modifier.weight(1f),
                onClick = { state.selectStoreSection(StoreSection.COURSES) },
            )
            ToggleChip(
                label = "✨ Cosmetics",
                selected = selectedSection == StoreSection.COSMETICS,
                modifier = Modifier.weight(1f),
                onClick = { state.selectStoreSection(StoreSection.COSMETICS) },
            )
        }
        when (selectedSection) {
            StoreSection.COURSES -> CoursesStoreShelf(state)
            StoreSection.COSMETICS -> CosmeticsStoreShelf(state)
        }
    }
}

@Composable
private fun CoursesStoreShelf(state: AppState) {
    val manifest = state.courseStoreManifest.collectAsState().value
    val loading = state.courseStoreLoading.collectAsState().value
    val error = state.courseStoreError.collectAsState().value
    val catalog = state.courseCatalog.collectAsState().value
    val downloadingId = state.courseDownloadId.collectAsState().value

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("COURSE PATHS")
        Text(
            "Choose a course to download it to this device. Courses stay available offline, and no file is downloaded until you choose it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading && manifest == null) EmptyMessage("Loading the course list from the repository…")
        if (!error.isNullOrBlank()) {
            EmptyMessage("Couldn't load the course list. Check your connection and try again. $error")
            OutlinedButton(
                onClick = { state.refreshCourseStore() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try again") }
        }
        val references = manifest?.courses.orEmpty()
        if (manifest != null && references.isEmpty()) EmptyMessage("There are no courses in the repository yet.")
        for (reference in references) {
            val course = catalog.course(reference.id)
            CourseStoreCard(
                reference = reference,
                downloadedCourse = course,
                isDownloading = downloadingId == reference.id,
                anotherDownloadInProgress = downloadingId != null && downloadingId != reference.id,
                onDownload = { state.downloadCourse(reference) },
                onUpdate = { state.updateCourse(reference) },
                onOpen = {
                    state.selectActiveCourse(reference.id)
                    state.switchTab(Route.Study)
                },
            )
        }
    }
}

@Composable
private fun CosmeticsStoreShelf(state: AppState) {
    val progress = state.learnerProgress.collectAsState().value
    val ready = state.progressionReady.collectAsState().value
    val category = remember { mutableStateOf(AppearanceCategory.HAIR_STYLE) }
    val unlockedCount = progress.ownedAppearanceItemIds.distinct().size

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                CharacterPortrait(progress.characterAppearance, width = 76.dp, height = 92.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SectionLabel("COSMETIC COLLECTION")
                    Text("${progress.coins} 🪙", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$unlockedCount of ${AppearanceCatalog.all.size} pieces unlocked",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Every purchase is just for looks.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            "Unlock a feature once, then change its size, spacing and height for free in Profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceCategory.entries.forEach { option ->
                ToggleChip(
                    label = option.storeTitle,
                    selected = category.value == option,
                    onClick = { category.value = option },
                )
            }
        }
        SectionLabel(category.value.storeTitle.uppercase())
        val items = AppearanceCatalog.inCategory(category.value)
        for (item in items) {
            AppearanceStoreCard(
                item = item,
                owned = item.id in progress.ownedAppearanceItemIds,
                coins = progress.coins,
                enabled = ready,
                onUnlock = { state.unlockAppearanceItem(item.id) },
            )
        }
        OutlinedButton(
            onClick = { state.switchTab(Route.Profile) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Style your character in Profile") }
    }
}

@Composable
private fun AppearanceStoreCard(item: AppearanceItem, owned: Boolean, coins: Long, enabled: Boolean, onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (owned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = item.swatchArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (item.swatchArgb == null) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.previewMark, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (owned) "UNLOCKED" else "${item.cost} coins",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (owned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (owned) {
                Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
            } else {
                Button(
                    onClick = onUnlock,
                    enabled = enabled && coins >= item.cost,
                ) { Text(if (coins >= item.cost) "Unlock" else "Need ${item.cost}") }
            }
        }
    }
}

@Composable
private fun CourseStoreCard(
    reference: CourseFileReference,
    downloadedCourse: LearningCourse?,
    isDownloading: Boolean,
    anotherDownloadInProgress: Boolean,
    onDownload: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reference.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (downloadedCourse != null) {
                    Text(
                        "DOWNLOADED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                reference.description.ifBlank { "A structured course with lessons and practice questions." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadedCourse != null) {
                Text(
                    "${downloadedCourse.lessons.size} lessons · saved for offline study",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloadedCourse == null) {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading && !anotherDownloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isDownloading) "Downloading…" else "Download course") }
            } else {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Study this course")
                }
                OutlinedButton(
                    onClick = onUpdate,
                    enabled = !isDownloading && !anotherDownloadInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isDownloading) "Updating…" else "Update course") }
            }
        }
    }
}
