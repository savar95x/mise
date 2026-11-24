@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Edit
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DiaryScreen(onLog: () -> Unit) {
    val nav = LocalNav.current
    val diary = Store.diary()
    val byMonth = remember(diary) { diary.groupBy { YearMonth.from(it.date) }.toList() }
    Box(Modifier.fillMaxSize()) {
        Page("Diary") { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { UpdateCard() }
                if (diary.isEmpty()) {
                    item {
                        Empty(
                            Icons.Rounded.AutoStories,
                            "Your diary is empty",
                            "Log what you watch, talk through what you thought, and keep it for later.",
                            "Log a watch",
                            onLog,
                        )
                    }
                } else {
                    item { YearCard() }
                    items(byMonth, key = { it.first.toString() }) { (ym, list) ->
                        Group(Fmt.month(ym.atDay(1))) {
                            list.forEach { w ->
                                val t = Store.title(w.titleId) ?: return@forEach
                                item { DiaryRow(t, w) { nav.open(Route.TitlePage(t.id)) } }
                            }
                        }
                    }
                }
            }
        }
        if (diary.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onLog,
                icon = { Icon(Icons.Rounded.Edit, null) },
                text = { Text("Log") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** This year at a glance. */
@Composable
private fun YearCard() {
    val year = LocalDate.now().year
    val films = Store.countThisYear(Kind.MOVIE, year)
    val shows = Store.countThisYear(Kind.TV, year)
    val allTime = Store.diary().count { it.date.year == year && it.tier == Tier.ALL_TIME }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("$year so far", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(films, if (films == 1) "film" else "films", Modifier.weight(1f))
                Stat(shows, if (shows == 1) "show" else "shows", Modifier.weight(1f))
                Stat(allTime, "all-time", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(n.toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun DiaryRow(t: Title, w: Watch, onClick: () -> Unit) {
    val rewatch = Store.isRewatch(w)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(t, 48.dp, corner = 8.dp, showName = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Fmt.day(w.date), w.season.takeIf { t.kind == Kind.TV && it.isNotBlank() }, "Rewatch".takeIf { rewatch })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            w.tier?.let { Text(it.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
        if (w.memos.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Mic, "Voice memo", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (rewatch) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Replay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Empty state: icon in a tonal circle, a line, a sentence, one button. */
@Composable
fun Empty(icon: ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** In-app update, as a card on the home screen (no pop-up). Same as the sibling apps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val s = Updater.state
    val rel = when (s) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.Failed -> s.release
        else -> null
    } ?: return
    var dismissed by remember(rel.version) { mutableStateOf(Updater.dismissedVersion(ctx) == rel.version) }
    if (dismissed && (s is Updater.State.Available || s is Updater.State.Ready)) return
    var notes by remember { mutableStateOf(false) }
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = on)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mise ${rel.version}", style = MaterialTheme.typography.titleMedium, color = on)
                    Text(
                        when (s) {
                            is Updater.State.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                            is Updater.State.Installing -> "Installing…"
                            is Updater.State.Ready -> "Downloaded, ready to install"
                            is Updater.State.Failed -> s.message
                            else -> "Update available" + if (rel.size > 0) " · ${"%.1f".format(rel.size / 1_048_576f)} MB" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s is Updater.State.Failed) MaterialTheme.colorScheme.error else on,
                    )
                }
            }
            if (s is Updater.State.Downloading) {
                LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 4.dp))
            }
            if (s is Updater.State.Installing) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 4.dp))
            }
            if (notes && rel.notes.isNotBlank()) {
                Text(rel.notes, style = MaterialTheme.typography.bodyMedium, color = on, modifier = Modifier.padding(start = 36.dp, top = 8.dp, end = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (rel.notes.isNotBlank()) TextButton(onClick = { notes = !notes }) { Text(if (notes) "Hide" else "What's new", color = on) }
                if (s is Updater.State.Available || s is Updater.State.Ready) {
                    TextButton(onClick = { Updater.dismiss(ctx, rel.version); dismissed = true }) { Text("Later", color = on) }
                }
                if (s !is Updater.State.Downloading && s !is Updater.State.Installing) {
                    TextButton(onClick = { Updater.update(ctx) }) {
                        Text(
                            when (s) {
                                is Updater.State.Failed -> "Retry"
                                is Updater.State.Ready -> "Install"
                                else -> "Update"
                            },
                            color = on,
                        )
                    }
                }
            }
        }
    }
}
