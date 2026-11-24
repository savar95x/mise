@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchFor { LOG, WATCHLIST }

/**
 * Find a film or show (TMDB), or add one by hand. Picking fetches its details (director, runtime, backdrop) and
 * saves the title, then hands it back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchSheet(purpose: SearchFor, onDismiss: () -> Unit, onPicked: (Title) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val token = Store.settings.tmdbToken
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Tmdb.Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query, token) {
        val q = query.trim()
        if (q.length < 2 || token.isBlank()) { hits = emptyList(); error = null; return@LaunchedEffect }
        delay(350)
        searching = true
        val r = withContext(Dispatchers.IO) { runCatching { Tmdb.search(token, q) } }
        searching = false
        r.onSuccess { hits = it; error = null }.onFailure { error = describeTmdb(it) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun pick(h: Tmdb.Hit) {
        if (opening != null) return
        val known = Store.title(h.id)
        opening = h.id
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Tmdb.details(token, h.kind, h.tmdbId) } }
            // Offline or TMDB hiccup: keep what the search gave us; details can be filled in later.
            val t = r.getOrNull() ?: known ?: Title(h.id, h.kind, h.name, h.year, h.tmdbId, h.poster, overview = h.overview)
            Store.putTitle(t)
            opening = null
            onPicked(Store.title(t.id) ?: t)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxHeight(0.94f).imePadding()) {
            Text(
                if (purpose == SearchFor.LOG) "What did you watch?" else "Add to watchlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Films and shows") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            if (searching) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (token.isBlank()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Search needs a free TMDB token", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add it in Settings → TMDB. Until then, add titles by hand below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                error?.let { e ->
                    item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(hits, key = { it.id }) { h -> HitRow(h, busy = opening == h.id, have = Store.title(h.id) != null) { pick(h) } }
                if (query.isNotBlank()) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ListItem(
                            modifier = Modifier.clickable { manual = true },
                            leadingContent = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Add “${query.trim()}” by hand") },
                            supportingContent = { Text("Not on TMDB, or no connection") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    if (manual) {
        ManualTitleDialog(query.trim(), onDismiss = { manual = false }) { t ->
            manual = false
            Store.putTitle(t)
            onPicked(t)
        }
    }
}

internal fun describeTmdb(e: Throwable): String = when {
    e is HttpStatusException && e.code == 401 -> "TMDB refused the token: check it in Settings"
    e is java.net.UnknownHostException -> "No connection"
    e is IllegalStateException -> e.message ?: "Add a TMDB token"
    else -> "Search failed: ${e.message}"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HitRow(h: Tmdb.Hit, busy: Boolean, have: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(enabled = !busy, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(Title(h.id, h.kind, h.name, h.year, h.tmdbId, h.poster), 48.dp, corner = 8.dp, showName = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(h.kind.label, h.year?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> LoadingIndicator(Modifier.size(32.dp))
                have -> Icon(Icons.Rounded.Check, "In your journal", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ManualTitleDialog(initial: String, onDismiss: () -> Unit, onSave: (Title) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var year by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.MOVIE) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add by hand") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(Kind.entries, kind, { it.label }) { kind = it }
                OutlinedTextField(name, { name = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    year, { v -> year = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Year") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(Title(Title.customId(), kind, name.trim(), year.toIntOrNull())) },
            ) { Text("Add") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
