package com.g1lg1l.stash.ui

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.raw
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

object Radius {
    val card = 24.dp
    val thumbnail = 12.dp
}

/** Material You: every color comes from the wallpaper. Android 12 is the minimum, so dynamic color is always there. */
@Composable
fun StashTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed hues, so a category reads the same whatever the wallpaper. Used for dots, icons and soft tints. */
val Category.tint: Color
    get() = when (this) {
        Category.FOOD -> Color(0xFFFF9F0A)
        Category.TRAVEL -> Color(0xFF30B0C7)
        Category.FITNESS -> Color(0xFF34C759)
        Category.IDEAS -> Color(0xFFFFC300)
        Category.BUSINESS -> Color(0xFF5E5CE6)
        Category.TECH -> Color(0xFF0A84FF)
        Category.SHOPPING -> Color(0xFFFF375F)
        Category.MUSIC -> Color(0xFFBF5AF2)
        Category.LEARNING -> Color(0xFF00C7BE)
        Category.PLACES -> Color(0xFFFF453A)
        Category.OTHER -> Color(0xFF8E8E93)
    }

@get:DrawableRes
val Category.icon: Int
    get() = when (this) {
        Category.FOOD -> R.drawable.ic_restaurant
        Category.TRAVEL -> R.drawable.ic_flight
        Category.FITNESS -> R.drawable.ic_directions_run
        Category.IDEAS -> R.drawable.ic_lightbulb
        Category.BUSINESS -> R.drawable.ic_work
        Category.TECH -> R.drawable.ic_memory
        Category.SHOPPING -> R.drawable.ic_shopping_bag
        Category.MUSIC -> R.drawable.ic_music_note
        Category.LEARNING -> R.drawable.ic_menu_book
        Category.PLACES -> R.drawable.ic_pin_drop
        Category.OTHER -> R.drawable.ic_link
    }

@get:DrawableRes
val Source.icon: Int
    get() = when (this) {
        Source.INSTAGRAM -> R.drawable.ic_photo_camera
        Source.YOUTUBE -> R.drawable.ic_smart_display_filled
        Source.TIKTOK -> R.drawable.ic_video_library
        Source.REDDIT -> R.drawable.ic_forum
        Source.X -> R.drawable.ic_chat
        Source.SPOTIFY -> R.drawable.ic_music_note
        Source.MAPS -> R.drawable.ic_location_on
        Source.WEB -> R.drawable.ic_language
        Source.UNKNOWN -> R.drawable.ic_link
    }

@get:DrawableRes
val ContentType.icon: Int
    get() = when (this) {
        ContentType.VIDEO -> R.drawable.ic_smart_display
        ContentType.ARTICLE -> R.drawable.ic_article
        ContentType.POST -> R.drawable.ic_chat
        ContentType.PRODUCT -> R.drawable.ic_shopping_bag
        ContentType.PLACE -> R.drawable.ic_pin_drop
        ContentType.MUSIC -> R.drawable.ic_music_note
        ContentType.OTHER -> R.drawable.ic_link
    }

enum class Theme(val label: String, val nightMode: Int) {
    SYSTEM("System", UiModeManager.MODE_NIGHT_AUTO),
    LIGHT("Light", UiModeManager.MODE_NIGHT_NO),
    DARK("Dark", UiModeManager.MODE_NIGHT_YES),
}

enum class FeedLayout { CARDS, COMPACT }

/** Preferences, mirrored into Compose state so every screen follows a change at once. */
object Prefs {
    lateinit var store: SharedPreferences
        private set

    fun init(context: Context) {
        store = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    // The property name is the key, the same as on iOS.

    /** Applied by the system through [UiModeManager.setApplicationNightMode]; stored here only to show the choice. */
    var theme by enumPref(Theme.SYSTEM)
    var feedLayout by enumPref(FeedLayout.CARDS)
    var showRediscovery by Pref({ it as? Boolean ?: true }) { key, value -> putBoolean(key, value) }
    var seenWelcome by Pref({ it as? Boolean ?: false }) { key, value -> putBoolean(key, value) }

    // The account session and the sync cursors. App-private, so the tokens stay with the app.
    var email by stringPref()
    var accessToken by stringPref()
    var refreshToken by stringPref()
    /** Unix seconds, as the server sends it. */
    var expiresAt by longPref()
    var lastPushedAt by longPref()
    /** The server's last `updated_at`, the exact string it sent. */
    var pullCursor by stringPref()
    var lastSyncedAt by longPref()
    var syncError by stringPref()

    private fun stringPref() = Pref({ it as? String }) { key, value -> putString(key, value) }
    private fun longPref() = Pref({ it as? Long ?: 0L }) { key, value -> putLong(key, value) }

    private inline fun <reified E : Enum<E>> enumPref(default: E) =
        Pref({ raw -> enumValues<E>().find { it.raw == raw } ?: default }) { key, value -> putString(key, value.raw) }

    private class Pref<T>(
        private val read: (Any?) -> T,
        private val write: SharedPreferences.Editor.(key: String, value: T) -> Unit,
    ) : ReadWriteProperty<Any, T> {
        private var state: MutableState<T>? = null

        private fun state(key: String) = state ?: mutableStateOf(read(store.all[key])).also { state = it }

        override fun getValue(thisRef: Any, property: KProperty<*>): T = state(property.name).value

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            state(property.name).value = value
            store.edit { write(property.name, value) }
        }
    }
}
