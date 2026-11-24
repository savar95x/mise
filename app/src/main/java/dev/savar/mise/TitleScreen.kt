@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TitleScreen(id: String) {
    val nav = LocalNav.current
    val t = Store.title(id)
    if (t == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val watches = Store.watchesOf(id)
    var story by remember { mutableStateOf<Watch?>(null) }
    var deleteWatch by remember { mutableStateOf<Watch?>(null) }
    var deleteTitle by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header(t, onDelete = { deleteTitle = true }) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-56).dp), verticalAlignment = Alignment.Bottom) {
                Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp) { Poster(t, 112.dp, corner = 14.dp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Text(t.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = Fmt.meta(t)
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (t.genres.isNotEmpty()) {
                        Text(t.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(Modifier.offset(y = (-36).dp).padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.open(Route.Log(t.id)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (watches.isEmpty()) "Log a watch" else "Log again")
                    }
                    FilledTonalButton(onClick = {
                        Store.setWatchlist(t.id, !t.onWatchlist)
                        nav.toast(if (t.onWatchlist) "Removed from watchlist" else "Added to watchlist")
                    }) {
                        Icon(if (t.onWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (t.onWatchlist) "On watchlist" else "Watchlist")
                    }
                }
                if (t.overview.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        t.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (overview) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { overview = !overview }.animateContentSize(),
                    )
                }
                if (watches.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (watches.size == 1) "Your watch" else "Your watches · ${watches.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        items(watches, key = { it.id }) { w ->
            WatchCard(
                t, w,
                onEdit = { nav.open(Route.Log(t.id, w.id)) },
                onStory = { story = w },
                onDelete = { deleteWatch = w },
                modifier = Modifier.offset(y = (-24).dp).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }

    story?.let { w -> StorySheet(t, w) { story = null } }
    deleteWatch?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteWatch = null },
            title = { Text("Delete this watch?") },
            text = { Text("Your thoughts from ${Fmt.longDay(w.date)}" + if (w.memos.isNotEmpty()) " and its voice memos will be gone." else " will be gone.") },
            confirmButton = { TextButton(onClick = { Store.deleteWatch(w.id); deleteWatch = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteWatch = null }) { Text("Cancel") } },
        )
    }
    if (deleteTitle) {
        AlertDialog(
            onDismissRequest = { deleteTitle = false },
            title = { Text("Remove ${t.name}?") },
            text = { Text(if (watches.isEmpty()) "It comes off your watchlist." else "Every watch of it, with its thoughts and voice memos, will be deleted.") },
            confirmButton = { TextButton(onClick = { deleteTitle = false; Store.deleteTitle(t.id); nav.back() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleteTitle = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(t: Title, onDelete: () -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface
    // A wide backdrop when there is one; otherwise just room for the top bar above the poster.
    Box(if (t.backdrop.isNotBlank()) Modifier.fillMaxWidth().aspectRatio(16f / 10f) else Modifier.fillMaxWidth().statusBarsPadding().height(120.dp)) {
        if (t.backdrop.isNotBlank()) Backdrop(t, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to surface.copy(alpha = 0.35f), 0.35f to Color.Transparent, 1f to surface),
            ),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { nav.back() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = surface.copy(alpha = 0.7f), contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            Box {
                FilledIconButton(
                    onClick = { menu = true },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = surface.copy(alpha = 0.7f), contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (t.tmdbId != null) {
                        DropdownMenuItem(
                            text = { Text("Refresh details") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = {
                                menu = false
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { runCatching { Tmdb.details(Store.settings.tmdbToken, t.kind, t.tmdbId) } }
                                    r.onSuccess { Store.putTitle(it); nav.toast("Details updated") }.onFailure { nav.toast(describeTmdb(it)) }
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Mise") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchCard(t: Title, w: Watch, onEdit: () -> Unit, onStory: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.longDay(w.date), style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(
                        w.season.takeIf { t.kind == Kind.TV && it.isNotBlank() },
                        "Rewatch".takeIf { Store.isRewatch(w) },
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = onStory) { Icon(Icons.Rounded.Share, "Share to story") }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (w.tier != null || w.scores.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        w.tier?.let { Pill(it.label, strong = true) }
                        Axis.entries.forEach { a -> w.scores[a]?.let { Pill("${a.label} ${Fmt.score(it)}") } }
                    }
                }
                if (w.mood.isNotBlank()) Labeled("State of mind", w.mood)
                if (w.thoughts.isNotBlank()) {
                    Column(Modifier.clickable { expanded = !expanded }) {
                        Text("Thoughts", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Text(
                            w.thoughts,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (w.memos.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { w.memos.forEach { MemoRow(it) } }
                }
                if (w.setup.isNotEmpty()) {
                    Text(
                        w.setup.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (w.line.isNotBlank()) Labeled("On the story", "“${w.line}”")
                if (w.tier == null && w.scores.isEmpty() && w.mood.isBlank() && w.thoughts.isBlank() && w.memos.isEmpty()) {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) { Text("Add your thoughts") }
                }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Pill(text: String, strong: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape).background(if (strong) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (strong) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

/** Play / pause a memo, with a wavy progress line while it plays. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemoRow(m: Memo, onDelete: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val ctx = LocalContext.current
    val exists = remember(m.file) { Store.memoFile(ctx, m).exists() }
    val playing = Player.playing == m.file
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { Player.toggle(ctx, m) }, enabled = exists) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
        }
        Column(Modifier.weight(1f)) {
            if (playing) {
                LinearWavyProgressIndicator(progress = { Player.progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            } else {
                Text(
                    if (exists) "Voice memo · ${Fmt.duration(m.durationMs)}" else "Voice memo · on another phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (exists) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete memo") }
    }
}
