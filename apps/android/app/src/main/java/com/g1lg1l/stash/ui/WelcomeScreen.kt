package com.g1lg1l.stash.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.g1lg1l.stash.Auth
import com.g1lg1l.stash.R
import com.g1lg1l.stash.Tabs

/** First launch only. Whatever the choice, it never shows again. */
@Composable
fun WelcomeScreen() {
    val backStack = LocalBackStack.current
    fun leave(then: NavKey? = null) {
        Prefs.seenWelcome = true
        backStack[backStack.lastIndex] = Tabs
        then?.let(backStack::add)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(160.dp),
            )
            Text("Stash", style = MaterialTheme.typography.displaySmall)
            Text(
                "Share it now. Find it later.", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Point(R.drawable.ic_share, "Share any link to Stash, from any app.")
                Point(R.drawable.ic_lightbulb, "Titles and pictures fill in, and saves sort themselves by topic.")
                Point(R.drawable.ic_sync, "An account is optional. It keeps your saves in sync across your devices.")
            }
            Spacer(Modifier.height(Spacing.xl))
            Button({ leave(Auth(create = false)) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Sign in") }
            FilledTonalButton({ leave(Auth(create = true)) }, Modifier.fillMaxWidth().padding(top = Spacing.xs).height(56.dp)) {
                Text("Create account")
            }
            TextButton({ leave() }, Modifier.fillMaxWidth().padding(top = Spacing.xs).height(56.dp)) { Text("Not now") }
        }
    }
}

@Composable
private fun Point(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
