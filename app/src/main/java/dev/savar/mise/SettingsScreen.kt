@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.mise

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val SPEECH_LANGS = listOf(
    "" to "Phone's language",
    "en-IN" to "English (India)",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "hi-IN" to "Hindi",
)

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val s = Store.settings
    var fontSheet by remember { mutableStateOf(false) }
    var tokenDialog by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    var appIcon by remember { mutableStateOf(AppIcon.current(ctx)) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch { Backup.export(ctx, uri).onSuccess { nav.toast(it) }.onFailure { nav.toast("Backup failed: ${it.message}") } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { Backup.import(ctx, uri).onSuccess { nav.toast(it) }.onFailure { nav.toast("Import failed: ${it.message}") } }
    }

    Page("Settings") { pad ->
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Group("Look") {
                item {
                    Pad {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Segmented(ThemeMode.entries, ThemePrefs.mode, { it.label }) { ThemePrefs.setMode(ctx, it) }
                    }
                }
                item {
                    SwitchRow(
                        "Pure black",
                        ThemePrefs.pureBlack,
                        enabled = isAppDark(),
                        icon = Icons.Rounded.Contrast,
                        summary = "For AMOLED screens, in dark theme",
                    ) { ThemePrefs.setPureBlack(ctx, it) }
                }
                item {
                    NavRow(Icons.Rounded.FontDownload, "Font", Fonts.family.ifBlank { "System default" }) { fontSheet = true }
                }
                item { AppIconRow(appIcon) { iconSheet = true } }
            }

            Group("Journal") {
                item {
                    NavRow(
                        Icons.Rounded.Movie,
                        "TMDB",
                        if (s.tmdbToken.isBlank()) "Not set: search needs a free token" else "Token set (${s.tmdbToken.take(4)}…)",
                    ) { tokenDialog = true }
                }
                item {
                    NavRow(
                        Icons.Rounded.Language,
                        "Transcription language",
                        SPEECH_LANGS.firstOrNull { it.first == s.speechLang }?.second ?: s.speechLang,
                    ) { langDialog = true }
                }
            }

            Group("Sync and backup") {
                item {
                    val c = Sync.config
                    NavRow(
                        Icons.Rounded.CloudSync,
                        "Supabase sync",
                        when {
                            !c.configured -> "Off · text only, voice memos stay here"
                            !c.signedIn -> "Not signed in"
                            Sync.busy -> "Syncing…"
                            Sync.error != null -> "Last try failed: ${Sync.error}"
                            else -> "${c.email} · synced ${Fmt.ago(Sync.lastSync)}"
                        },
                    ) { nav.open(Route.SyncPage) }
                }
                item {
                    NavRow(Icons.Rounded.Backup, "Back up everything", "One .zip with your journal and every voice memo") {
                        exporter.launch(Backup.fileName())
                    }
                }
                item {
                    NavRow(Icons.Rounded.Restore, "Restore from backup", "Merges in: newer edits win, nothing is wiped") {
                        importer.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                }
            }

            Group("About") {
                item {
                    val version = remember {
                        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                    }
                    val u = Updater.state
                    NavRow(
                        Icons.Rounded.SystemUpdate,
                        "Mise $version",
                        when (u) {
                            is Updater.State.Available -> "Update available: ${u.release.version}"
                            is Updater.State.Ready -> "${u.release.version} downloaded, tap to install"
                            is Updater.State.Downloading -> "Downloading ${u.release.version}… ${(u.progress * 100).toInt()}%"
                            is Updater.State.Installing -> "Installing ${u.release.version}…"
                            is Updater.State.Failed -> "Update failed: ${u.message}"
                            Updater.State.UpToDate -> Updater.checkError?.let { "Couldn't check: $it" } ?: "Up to date · checked ${Fmt.ago(Updater.lastCheck)}"
                            Updater.State.Idle -> Updater.checkError?.let { "Couldn't check: $it · tap to retry" } ?: "Tap to check for updates"
                        },
                    ) {
                        when (u) {
                            is Updater.State.Available, is Updater.State.Ready, is Updater.State.Failed -> Updater.update(ctx)
                            is Updater.State.Downloading, is Updater.State.Installing -> Unit
                            else -> Updater.check(ctx)
                        }
                    }
                }
                item {
                    NavRow(Icons.Rounded.Code, "Source code", "github.com/savar95x/mise") {
                        openUrl(ctx, "https://github.com/savar95x/mise")
                    }
                }
                item {
                    Pad {
                        Text(
                            "Film and show details from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. Your thoughts, scores and voice memos never leave your phone unless you turn on sync (text only).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }

    if (fontSheet) FontSheet { fontSheet = false }
    if (iconSheet) AppIconSheet(appIcon, onPick = { AppIcon.set(ctx, it); appIcon = it }, onDismiss = { iconSheet = false })
    if (langDialog) {
        AlertDialog(
            onDismissRequest = { langDialog = false },
            title = { Text("Transcription language") },
            text = {
                Column {
                    Text(
                        "What you speak in. Offline transcription needs the language downloaded in your phone's speech settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    SPEECH_LANGS.forEach { (tag, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .selectable(selected = s.speechLang == tag, role = Role.RadioButton) {
                                    Store.updateSettings(ctx) { it.copy(speechLang = tag) }
                                    langDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.speechLang == tag, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { langDialog = false }) { Text("Done") } },
        )
    }
    if (tokenDialog) {
        var token by remember { mutableStateOf(s.tmdbToken) }
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text("TMDB token") },
            text = {
                Column {
                    Text(
                        "Free from themoviedb.org: Settings → API. Paste the API Read Access Token (the long one) or the API Key.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        singleLine = true,
                        label = { Text("Token or key") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { openUrl(ctx, "https://www.themoviedb.org/settings/api") }) {
                        Text("Get a free token")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.updateSettings(ctx) { it.copy(tmdbToken = token) }
                    tokenDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Font picker: System default, a short list of picks (each shown in its own face), and "More fonts" for the full
 * Google Fonts catalogue. Picking downloads in place with a spinner on that row; the sheet closes when it's applied.
 */
@Composable
fun FontSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var browse by remember { mutableStateOf(false) }

    fun apply(name: String?) {
        if (busy != null) return
        error = null
        if (name == null) { Fonts.useSystem(ctx); onDismiss(); return }
        if (Fonts.family.equals(name, true)) { onDismiss(); return }
        busy = name
        scope.launch {
            Fonts.use(ctx, name)
                .onSuccess { busy = null; onDismiss() }
                .onFailure { busy = null; error = it.message ?: "Couldn't download $name" }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Font", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            FontChoice("System default", null, selected = Fonts.family.isBlank(), busy = false) { apply(null) }
            val shown = (Fonts.picks + listOfNotNull(Fonts.family.takeIf { f -> f.isNotBlank() && Fonts.picks.none { it.equals(f, true) } }))
            shown.forEach { name ->
                FontChoice(name, name, selected = Fonts.family.equals(name, true), busy = busy == name) { apply(name) }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { browse = true }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("More fonts", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }
    if (browse) FontBrowserSheet(selected = Fonts.family, onPick = { browse = false; apply(it) }, onDismiss = { browse = false })
}

/** One radio row; [family] null = system font. The name is drawn in its own face once a tiny preview arrives. */
@Composable
private fun FontChoice(label: String, family: String?, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val tf by produceState<Typeface?>(null, family) { if (family != null) value = Fonts.previewTypeface(ctx, family) }
    val face = remember(tf) { tf?.let { FontFamily(it) } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (busy) LoadingIndicator(Modifier.size(24.dp)) else RadioButton(selected = selected, onClick = null)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (family == null) FontFamily.Default else face,
            modifier = Modifier.weight(1f),
        )
    }
}

private val CATEGORIES = listOf(
    "All" to null,
    "Sans" to "Sans Serif",
    "Serif" to "Serif",
    "Display" to "Display",
    "Mono" to "Monospace",
    "Handwriting" to "Handwriting",
)

/** Every Google Fonts family, searchable and filterable, each previewed in its own face. */
@Composable
fun FontBrowserSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val families by produceState(Fonts.cachedFamilies(ctx)) { value = Fonts.loadCatalogue(ctx) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val shown = remember(families, query, category) {
        val q = query.trim().lowercase().replace(" ", "")
        families.filter { f ->
            (category == null || f.category == category) && (q.isEmpty() || f.name.lowercase().replace(" ", "").contains(q))
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, category) { listState.scrollToItem(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text("Google Fonts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search ${if (families.isEmpty()) "" else "${families.size} "}fonts") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES) { (label, value) -> Chip(selected = category == value, onClick = { category = value }) { Text(label) } }
            }
            HorizontalDivider()
            if (families.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(shown, key = { it.name }) { fam -> FontRow(fam, fam.name.equals(selected, ignoreCase = true)) { onPick(fam.name) } }
            }
        }
    }
}

@Composable
private fun FontRow(fam: Fonts.Family, isSelected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val tf by produceState<Typeface?>(null, fam.name) { value = Fonts.previewTypeface(ctx, fam.name) }
    val family = remember(tf) { tf?.let { FontFamily(it) } }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                fam.name,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = family,
                color = if (family == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Fonts.PREVIEW_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = family,
                color = scheme.onSurfaceVariant,
            )
        }
        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = scheme.primary)
    }
}


/** Launcher-style tile for an app icon: the family's dark tile with the mark, cropped like a launcher does. */
@Composable
fun IconTile(icon: AppIcon, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        // Launchers show the middle ~2/3 of the 108dp adaptive canvas.
        Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

@Composable
private fun AppIconRow(icon: AppIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("App icon", style = MaterialTheme.typography.bodyLarge)
            Text(icon.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Grid of every launcher icon; tapping one switches the home-screen icon straight away. */
@Composable
fun AppIconSheet(current: AppIcon, onPick: (AppIcon) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("App icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
            AppIcon.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { icon ->
                        val selected = icon == current
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton) { if (!selected) onPick(icon) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconTile(icon, 64.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                icon.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "The home screen can take a few seconds to update. If Mise's icon disappears from it, add it again from the app drawer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
