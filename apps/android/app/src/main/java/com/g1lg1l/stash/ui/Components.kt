package com.g1lg1l.stash.ui

import android.content.Context
import android.content.Intent
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImage
import com.g1lg1l.stash.Detail
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.byline
import com.g1lg1l.stash.data.displayName
import com.g1lg1l.stash.data.displayTitle
import com.g1lg1l.stash.data.mediaAspectRatio
import com.g1lg1l.stash.data.seen
import com.g1lg1l.stash.data.showsMedia
import com.g1lg1l.stash.data.withCategory
import kotlinx.coroutines.launch

val LocalBackStack = staticCompositionLocalOf<MutableList<NavKey>> { error("No back stack") }

/** Pushes a screen, unless it's already showing: a quick double tap opens it once. */
fun MutableList<NavKey>.open(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The same image on a card and on the detail screen grows from one into the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMedia(save: Save): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    return with(shared) {
        sharedElement(rememberSharedContentState("media-${save.id}"), LocalNavAnimatedContentScope.current)
    }
}

/**
 * Fills whatever bounds it's given. Callers set the aspect ratio and clip shape.
 * Missing or failed images fall back to a category-tinted gradient, so layouts never collapse.
 */
@Composable
fun SaveThumbnail(save: Save, modifier: Modifier = Modifier) {
    val tint = save.category.tint
    Box(
        modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.42f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (save.category == Category.OTHER) save.source.icon else save.category.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
        // ponytail: Coil's default memory and disk caches; tune sizes if scrolling hitches at thousands of saves.
        AsyncImage(
            model = save.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Source name on a translucent pill, for placing over media. */
@Composable
fun SourceBadge(save: Save, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(save.source.icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(save.source.displayName, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun CategoryLabel(category: Category) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(category.tint, CircleShape))
        Text(category.displayName)
    }
}

/** "Food · @weeknight.pasta" */
@Composable
fun MetaLine(save: Save) {
    ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryLabel(save.category)
            Text("·", Modifier.clearAndSetSemantics {})
            Text(save.byline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SourceLabel(save: Save) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(save.source.icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(save.source.displayName)
    }
}

/** Large visual save. The layout follows the content type instead of one generic container. */
@Composable
fun SaveCard(save: Save) {
    when {
        save.contentType == ContentType.MUSIC -> MusicCard(save)
        save.showsMedia -> MediaCard(save)
        else -> TextCard(save)
    }
}

// Video, product, place, and anything with an image.
@Composable
private fun MediaCard(save: Save) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box {
            SaveThumbnail(
                save,
                Modifier.fillMaxWidth().aspectRatio(save.mediaAspectRatio).sharedMedia(save).clip(RoundedCornerShape(Radius.card)),
            )
            SourceBadge(save, Modifier.align(Alignment.TopStart).padding(Spacing.s))
            // Without a real image the placeholder symbol already says "video".
            if (save.contentType == ContentType.VIDEO && save.thumbnailUrl != null) {
                Surface(Modifier.align(Alignment.Center), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                    Icon(painterResource(R.drawable.ic_play_arrow_filled), contentDescription = null, modifier = Modifier.padding(Spacing.m).size(28.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            when (save.contentType) {
                ContentType.PLACE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(painterResource(R.drawable.ic_location_on), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(save.displayTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                }
                ContentType.ARTICLE -> Text(save.displayTitle, style = serifTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                else -> Text(
                    save.displayTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            if (save.contentType == ContentType.ARTICLE) save.descriptionText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            MetaLine(save)
        }
    }
}

// Articles and posts without an image: editorial type on a soft category tint.
@Composable
private fun TextCard(save: Save) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(save.category.tint.copy(alpha = 0.13f), RoundedCornerShape(Radius.card))
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            SourceLabel(save)
        }
        Text(save.displayTitle, style = serifTitle)
        save.descriptionText?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Spacing.xxs))
        MetaLine(save)
    }
}

@Composable
private fun MusicCard(save: Save) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.card))
            .padding(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SaveThumbnail(save, Modifier.size(88.dp).sharedMedia(save).clip(RoundedCornerShape(Radius.thumbnail)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(save.displayTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(save.byline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                SourceLabel(save)
            }
        }
    }
}

val serifTitle @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)

/** Compact save for dense lists and search results. */
@Composable
fun SaveRow(save: Save) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // Mail-style unseen dot; the row keeps its alignment either way.
        Box(
            Modifier
                .size(8.dp)
                .background(if (save.openedAt == null) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .semantics { if (save.openedAt == null) contentDescription = "Unseen" },
        )
        SaveThumbnail(save, Modifier.size(56.dp).sharedMedia(save).clip(RoundedCornerShape(Radius.thumbnail)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(save.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MetaLine(save)
        }
    }
}

/** Tap opens the save, touch and hold shows its actions. Cards press in (like iOS); rows ripple. */
@Composable
fun SaveItem(save: Save, modifier: Modifier = Modifier, ripple: Boolean = false, content: @Composable () -> Unit) {
    val backStack = LocalBackStack.current
    var showActions by rememberSaveable(save.id) { mutableStateOf(false) }
    Box(
        modifier.pressable(ripple = ripple, onLongClick = { showActions = true }, onClickLabel = "Open") { backStack.open(Detail(save.id)) },
    ) { content() }
    if (showActions) SaveActionsSheet(save) { showActions = false }
}

/** A tactile press: shrinks a little, or ripples for rows. Touch and hold gives a haptic tick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    ripple: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !ripple) 0.97f else 1f, label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.combinedClickable(
        interactionSource = interaction,
        indication = if (ripple) ripple() else null,
        onClickLabel = onClickLabel,
        onLongClickLabel = onLongClick?.let { "Show actions" },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = modifier.semantics { heading() })
}

@Composable
fun BackButton() {
    val backStack = LocalBackStack.current
    IconButton({ backStack.removeLastOrNull() }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
}

/** Lets a full-width header ignore a grid's side padding, so its content scrolls edge to edge. */
fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Every action on a save, on touch and hold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveActionsSheet(save: Save, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Row(
            Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SaveThumbnail(save, Modifier.size(48.dp).clip(RoundedCornerShape(Radius.thumbnail)))
            Column(Modifier.weight(1f)) {
                Text(save.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaLine(save)
            }
        }
        SheetAction("Open original", R.drawable.ic_open_in_new) { close { context.openOriginal(save) } }
        SheetAction("Share", R.drawable.ic_share) { close { context.share(save.url) } }
        val seen = save.openedAt != null
        SheetAction(if (seen) "Mark as unseen" else "Mark as seen", if (seen) R.drawable.ic_visibility_off else R.drawable.ic_visibility) {
            Stash.edit(save) { if (seen) it.copy(openedAt = null) else it.seen() }
            close()
        }
        Text(
            "Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.xs),
        )
        CategoryChips(save.category, Modifier.padding(horizontal = Spacing.l)) { category ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            Stash.edit(save) { it.withCategory(category) }
        }
        Spacer(Modifier.height(Spacing.s))
        SheetAction("Delete", R.drawable.ic_delete, MaterialTheme.colorScheme.error) { close { deleteWithUndo(save, snackbar) } }
    }
}

@Composable
private fun SheetAction(label: String, @DrawableRes icon: Int, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val tint = color.takeOrElse { MaterialTheme.colorScheme.onSurface }
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = Spacing.xs),
    )
}

/** Pick a category by hand; the choice sticks. Used in the actions sheet. */
@Composable
fun CategoryChips(selected: Category, modifier: Modifier = Modifier, onSelect: (Category) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Category.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.displayName) },
                leadingIcon = { Icon(painterResource(category.icon), contentDescription = null, tint = category.tint, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** Compact row: swipe toward the end to toggle seen, toward the start to delete (with undo). */
@Composable
fun SwipeableSaveRow(save: Save, modifier: Modifier = Modifier) {
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Not saveable on purpose: a row brought back by Undo must not come back swiped away.
    val state = remember(save.id) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { it * 0.4f } }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, seen = save.openedAt != null) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    Stash.edit(save) { if (it.openedAt != null) it.copy(openedAt = null) else it.seen() }
                    scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.EndToStart -> deleteWithUndo(save, snackbar)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SaveItem(save, ripple = true) { SaveRow(save) }
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, seen: Boolean) {
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            MaterialTheme.colorScheme.primaryContainer, if (seen) R.drawable.ic_visibility_off else R.drawable.ic_visibility, Alignment.CenterStart,
        )
        SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.errorContainer, R.drawable.ic_delete, Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled -> return
    }
    Box(Modifier.fillMaxSize().background(color).padding(horizontal = Spacing.l), contentAlignment = alignment) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

/** Centered message for empty screens, with an optional way out. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(20.dp).size(36.dp))
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { (label, onClick) -> FilledTonalButton(onClick, Modifier.padding(top = Spacing.xs)) { Text(label) } }
    }
}

fun Context.openOriginal(save: Save) {
    Stash.edit(save) { it.seen() }
    // App links hand YouTube, Spotify, Maps etc. to their apps; everything else opens in the browser.
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, save.url.toUri())) }
}

fun Context.share(text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
    startActivity(Intent.createChooser(send, null))
}

/** Deletes right away and offers Undo. The cached thumbnail goes only once Undo is no longer possible. */
fun deleteWithUndo(save: Save, snackbar: SnackbarHostState) {
    Stash.scope.launch {
        Stash.dao.delete(save)
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar("Removed from your stash", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) Stash.dao.upsert(save) else Stash.forgetThumbnail(save)
    }
}

/** "now", "5 minutes ago", "yesterday", "last week", "3 months ago". */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val formatter = RelativeDateTimeFormatter.getInstance()
    val minutes = (now - millis) / 60_000
    val (amount, unit) = when {
        minutes < 1 -> return formatter.format(Direction.PLAIN, AbsoluteUnit.NOW)
        minutes < 60 -> minutes to RelativeUnit.MINUTES
        minutes < 24 * 60 -> minutes / 60 to RelativeUnit.HOURS
        minutes < 7 * 24 * 60 -> minutes / (24 * 60) to RelativeUnit.DAYS
        minutes < 30 * 24 * 60 -> minutes / (7 * 24 * 60) to RelativeUnit.WEEKS
        minutes < 365 * 24 * 60 -> minutes / (30 * 24 * 60) to RelativeUnit.MONTHS
        else -> minutes / (365 * 24 * 60) to RelativeUnit.YEARS
    }
    val named = mapOf(
        RelativeUnit.DAYS to AbsoluteUnit.DAY, RelativeUnit.WEEKS to AbsoluteUnit.WEEK,
        RelativeUnit.MONTHS to AbsoluteUnit.MONTH, RelativeUnit.YEARS to AbsoluteUnit.YEAR,
    )
    if (amount == 1L) named[unit]?.let { return formatter.format(Direction.LAST, it) }
    return formatter.format(amount.toDouble(), Direction.LAST, unit)
}
