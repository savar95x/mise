@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WatchlistScreen(onAdd: () -> Unit) {
    val nav = LocalNav.current
    val all = Store.watchlist()
    var filter by rememberSaveable { mutableStateOf<Kind?>(null) }
    val shown = all.filter { filter == null || it.kind == filter }
    Box(Modifier.fillMaxSize()) {
        Page("Watchlist") { pad ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (all.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Empty(
                            Icons.Rounded.BookmarkAdd,
                            "Nothing to watch yet",
                            "Keep the films and shows you mean to get to. Logging one takes it off the list.",
                            "Add a title",
                            onAdd,
                        )
                    }
                } else {
                    item(span = { GridItemSpan(3) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            Chip(filter == null, { filter = null }) { Text("All ${all.size}") }
                            Kind.entries.forEach { k ->
                                val n = all.count { it.kind == k }
                                if (n > 0) Chip(filter == k, { filter = if (filter == k) null else k }) { Text("${k.plural} $n") }
                            }
                        }
                    }
                    items(shown, key = { it.id }) { t -> PosterTile(t) { nav.open(Route.TitlePage(t.id)) } }
                }
            }
        }
        if (all.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun PosterTile(t: Title, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) { Poster(t, maxWidth, corner = 12.dp) }
        Spacer(Modifier.height(6.dp))
        Text(t.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(t.year?.toString(), t.kind.label.takeIf { t.kind == Kind.TV }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
