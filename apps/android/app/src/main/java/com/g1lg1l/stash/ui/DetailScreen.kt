package com.g1lg1l.stash.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.SaveStatus
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.displayName
import com.g1lg1l.stash.data.displayTitle
import com.g1lg1l.stash.data.mediaAspectRatio
import com.g1lg1l.stash.data.seen
import com.g1lg1l.stash.data.showsMedia
import com.g1lg1l.stash.data.withCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(save: Save?) {
    val backStack = LocalBackStack.current
    // Deleted (here or elsewhere) while open: nothing left to show.
    if (save == null) {
        LaunchedEffect(Unit) { backStack.removeLastOrNull() }
        return
    }
    val context = LocalContext.current
    val article = save.contentType == ContentType.ARTICLE
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var mediaHeight by remember { mutableIntStateOf(0) }
    val barHeight = with(LocalDensity.current) { (64.dp + 40.dp).roundToPx() }
    // Over the image the bar is clear with light icons; once the image scrolls away it turns solid.
    val showsMedia = save.showsMedia
    // Derived, so scrolling recomposes only when it crosses the image's edge, not every frame.
    val overMedia by remember(showsMedia) { derivedStateOf { showsMedia && scroll.value < mediaHeight - barHeight } }
    val barColor by animateColorAsState(if (overMedia) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer, label = "bar")
    val iconColor by animateColorAsState(if (overMedia) Color.White else MaterialTheme.colorScheme.onSurface, label = "icons")
    // Over an image the buttons sit on dark circles, like iOS's glass, so they read on bright pictures too.
    val buttonColor by animateColorAsState(if (overMedia) Color.Black.copy(alpha = 0.35f) else Color.Transparent, label = "buttons")
    val buttonColors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = iconColor)

    // Seen = opened. Unseen saves are what rediscovery resurfaces.
    LaunchedEffect(save.id) { if (save.openedAt == null) Stash.edit(save) { it.seen() } }
    LightStatusBarIcons(overMedia)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.verticalScroll(scroll)) {
            if (save.showsMedia) {
                Box(Modifier.onSizeChanged { mediaHeight = it.height }) {
                    SaveThumbnail(save, Modifier.fillMaxWidth().aspectRatio(save.mediaAspectRatio).sharedMedia(save))
                    // Keeps the status bar and the buttons legible over bright images.
                    Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
                }
            } else {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(64.dp))
            }

            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Header(save, article)

                // App links hand YouTube, Spotify, Maps etc. to their apps; everything else opens in the browser.
                Button({ context.openOriginal(save) }, Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Open original", style = MaterialTheme.typography.titleMedium)
                }

                save.summary?.let { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Summary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(summary, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                save.descriptionText?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (article) FontFamily.Serif else null))
                }

                if (save.tags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        save.tags.forEach { tag ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text("#$tag", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.s, vertical = 6.dp))
                            }
                        }
                    }
                }

                Footer(save)
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton({ backStack.removeLastOrNull() }, colors = buttonColors) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            },
            actions = {
                IconButton({ context.share(save.url) }, colors = buttonColors) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share")
                }
                IconButton({ confirmingDelete = true }, colors = buttonColors) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text("Delete this save?") },
            text = { Text("It will be removed from your stash for good.") },
            confirmButton = {
                TextButton({
                    confirmingDelete = false
                    backStack.removeLastOrNull()
                    Stash.scope.launch { Stash.delete(save) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(save: Save, article: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceLabel(save)
                Spacer(Modifier.weight(1f))
                CategoryMenu(save)
            }
        }
        Text(
            save.displayTitle,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold, fontFamily = if (article) FontFamily.Serif else null,
            ),
            modifier = Modifier.semantics { heading() },
        )
        save.author?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tap to recategorize; the choice sticks. */
@Composable
private fun CategoryMenu(save: Save) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        TextButton(
            { open = true },
            modifier = Modifier.semantics { stateDescription = save.category.displayName },
        ) {
            CategoryLabel(save.category)
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Change category", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            Category.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.displayName) },
                    leadingIcon = { Icon(painterResource(category.icon), contentDescription = null, tint = category.tint) },
                    trailingIcon = if (category == save.category) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected") }
                    } else null,
                    onClick = {
                        open = false
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        Stash.edit(save) { it.withCategory(category) }
                    },
                )
            }
        }
    }
}

@Composable
private fun Footer(save: Save) {
    ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (save.status) {
                SaveStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Details couldn't be loaded. The link is saved.")
                }
                SaveStatus.PENDING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text("Getting details…")
                }
                SaveStatus.ENRICHED -> Unit
            }
            Text("Saved ${relativeTime(save.lastSavedAt)}")
            SelectionContainer { Text(save.url, maxLines = 2) }
        }
    }
}

/** White status bar icons while the screen sits over an image; back to the theme's when it doesn't. */
@Composable
private fun LightStatusBarIcons(overMedia: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(overMedia, dark) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !overMedia && !dark
        onDispose { controller.isAppearanceLightStatusBars = !dark }
    }
}
