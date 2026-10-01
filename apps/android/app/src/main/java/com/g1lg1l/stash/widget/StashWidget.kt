package com.g1lg1l.stash.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.g1lg1l.stash.MainActivity
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.Rediscovery
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.byline
import com.g1lg1l.stash.data.displayTitle
import com.g1lg1l.stash.ui.icon
import com.g1lg1l.stash.ui.relativeTime
import com.g1lg1l.stash.ui.tint

class StashWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StashWidget()
}

/** One save on the Home screen: something worth another look, else the newest unseen one. Tapping opens it in Stash. */
class StashWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // The app is the only writer; this follows its store live while the widget is showing.
            val saves by Stash.saves.collectAsState()
            GlanceTheme { WidgetContent(saves?.let { Rediscovery.widgetPick(it) }, loading = saves == null) }
        }
    }

    private companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun WidgetContent(pick: Pair<Save, String>?, loading: Boolean) {
    val context = LocalContext.current
    val save = pick?.first
    val image by produceState<Bitmap?>(null, save?.thumbnailUrl) { value = save?.thumbnailUrl?.let { thumbnail(context, it) } }
    var modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        .background(GlanceTheme.colors.widgetBackground)
    if (save != null) {
        modifier = modifier.clickable(
            actionStartActivity(Intent(context, MainActivity::class.java).setData("stash://save/${save.id}".toUri())),
        )
    }
    Box(modifier) {
        when {
            loading -> Unit
            pick == null -> Empty()
            LocalSize.current.width < 200.dp -> Small(pick.first, pick.second, image)
            else -> Medium(pick.first, pick.second, image)
        }
    }
}

@Composable
private fun Small(save: Save, headline: String, image: Bitmap?) {
    Box(GlanceModifier.fillMaxSize()) {
        Artwork(save.category, image, symbol = false)
        Image(ImageProvider(R.drawable.widget_scrim), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            if (image == null) {
                Image(
                    ImageProvider(save.category.icon), contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(Color.White)), modifier = GlanceModifier.size(26.dp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                headline.uppercase(), maxLines = 1,
                style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                save.displayTitle, maxLines = 3,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun Medium(save: Save, headline: String, image: Bitmap?) {
    Row(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(120.dp).fillMaxHeight().cornerRadius(16.dp)) { Artwork(save.category, image, symbol = true) }
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Text(headline, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(2.dp))
            Text(save.displayTitle, maxLines = 3, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "${save.byline} · ${relativeTime(save.lastSavedAt)}", maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

/** The thumbnail, or the category's tint and symbol when there isn't one (the same fallback as in the app). */
@Composable
private fun Artwork(category: Category, image: Bitmap?, symbol: Boolean) {
    Box(GlanceModifier.fillMaxSize().background(category.tint), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(ImageProvider(image), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (symbol) {
            Image(
                ImageProvider(category.icon), contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)), modifier = GlanceModifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun Empty() {
    Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
        Image(
            ImageProvider(R.drawable.ic_inbox), contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary), modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text("Your stash is empty", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
        Text("Share a link to Stash.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}

/** Widgets can't load images themselves, and big ones are dropped: fetch and shrink here. */
private suspend fun thumbnail(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).size(600).allowHardware(false).build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}
