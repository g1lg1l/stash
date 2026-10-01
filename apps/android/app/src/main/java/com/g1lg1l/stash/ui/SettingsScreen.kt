package com.g1lg1l.stash.ui

import android.app.UiModeManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.getSystemService
import com.g1lg1l.stash.Auth
import com.g1lg1l.stash.R
import com.g1lg1l.stash.Tips
import com.g1lg1l.stash.data.Account
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.Sync
import com.g1lg1l.stash.data.displayTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(saves: List<Save>) {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" }
    }
    val savesStay = if (saves.size == 1) "Your save stays" else "Your ${saves.size} saves stay"
    var confirmingDelete by remember { mutableStateOf(false) }

    SettingsScaffold("Settings") {
        Section("Account")
        val email = Prefs.email
        if (email == null) {
            ListItem(
                headlineContent = { Text("Sync your saves") },
                supportingContent = { Text(Prefs.syncError ?: "Optional. Sign in to keep your saves in sync across your devices.") },
                leadingContent = { Icon(painterResource(R.drawable.ic_sync), contentDescription = null) },
            )
            Row(Modifier.padding(horizontal = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Button({ backStack.open(Auth(create = false)) }) { Text("Sign in") }
                OutlinedButton({ backStack.open(Auth(create = true)) }) { Text("Create account") }
            }
        } else {
            val lastSynced = Prefs.lastSyncedAt
            ListItem(
                headlineContent = { Text(email) },
                supportingContent = {
                    Text(
                        when {
                            Sync.syncing -> "Syncing…"
                            Prefs.syncError != null -> Prefs.syncError.orEmpty()
                            lastSynced == 0L -> "Not synced yet"
                            System.currentTimeMillis() - lastSynced < 60_000 -> "Synced just now"
                            else -> "Last synced ${relativeTime(lastSynced)}"
                        },
                    )
                },
                leadingContent = { Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Sync now") },
                leadingContent = { Icon(painterResource(R.drawable.ic_sync), contentDescription = null) },
                modifier = Modifier.clickable(enabled = !Sync.syncing) { Stash.scope.launch { Sync.sync() } },
            )
            ListItem(
                headlineContent = { Text("Sign out") },
                supportingContent = { Text("$savesStay on this phone.") },
                leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                modifier = Modifier.clickable { Account.signOut() },
            )
            ListItem(
                headlineContent = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmingDelete = true },
            )
        }

        Section("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
            Theme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = Prefs.theme == theme,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        Prefs.theme = theme
                        // The system remembers it and applies it to the splash screen too.
                        context.getSystemService<UiModeManager>()?.setApplicationNightMode(theme.nightMode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, Theme.entries.size),
                ) { Text(theme.label) }
            }
        }

        Section("Explore")
        ListItem(
            headlineContent = { Text("Worth another look") },
            supportingContent = { Text("Brings back saves you haven't opened in a few days.") },
            trailingContent = { Switch(Prefs.showRediscovery, onCheckedChange = null) },
            modifier = Modifier.clickable { Prefs.showRediscovery = !Prefs.showRediscovery },
        )

        Section("Your data")
        ListItem(
            headlineContent = { Text("Export links") },
            supportingContent = {
                Text("$savesStay on this phone. Export sends every title and link as plain text, to Keep, Drive or anywhere else.")
            },
            leadingContent = { Icon(painterResource(R.drawable.ic_upload), contentDescription = null) },
            modifier = Modifier.clickable(enabled = saves.isNotEmpty()) {
                context.share(saves.joinToString("\n\n") { "${it.displayTitle}\n${it.url}" }, subject = "My Stash")
            },
        )

        Section("About")
        ListItem(
            headlineContent = { Text("Get the most out of Stash") },
            leadingContent = { Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null) },
            modifier = Modifier.clickable { backStack.open(Tips) },
        )
        ListItem(
            headlineContent = { Text("Version") },
            trailingContent = { Text(version, style = MaterialTheme.typography.bodyMedium) },
            leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
        )
        ListItem(
            headlineContent = { Text("Source code on GitHub") },
            leadingContent = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
            trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
            modifier = Modifier.clickable { uriHandler.openUri("https://github.com/g1lg1l/stash") },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text("Delete your account?") },
            text = { Text("This deletes your account and the copy of your saves on the server. $savesStay on this phone.") },
            confirmButton = {
                TextButton({
                    confirmingDelete = false
                    Stash.scope.launch {
                        try {
                            Account.deleteAccount()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Prefs.syncError = Account.describe(e)
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Setup that makes saving one tap away. Android tucks share targets and text actions away, so most people never find them. */
@Composable
fun TipsScreen() {
    SettingsScaffold("Get the most out of Stash") {
        Tip(
            R.drawable.ic_push_pin, "Pin Stash in the share sheet",
            "Share something, then touch and hold Stash in the list of apps and tap Pin. It stays at the top from then on.",
        )
        Tip(
            R.drawable.ic_text_select_start, "Save any link you select",
            "Select a link in any app, open the menu next to Copy and tap Save to Stash.",
        )
        Tip(
            R.drawable.ic_widgets, "Add the widget",
            "Touch and hold the Home screen, tap Widgets and find Stash. It brings back something you saved; tap it to open.",
        )
        Tip(
            R.drawable.ic_grid_view, "Come back to Explore",
            "Worth another look picks saves you haven't opened in a few days. Opening one retires it.",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, content: @Composable () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(title) }, navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) { content() }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
    )
}

@Composable
private fun Tip(@DrawableRes icon: Int, title: String, detail: String) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    )
}
