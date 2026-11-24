@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Preview of the story card, what's on it, and where it goes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun StorySheet(t: Title, w: Watch, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prefs = remember { ctx.getSharedPreferences("story", android.content.Context.MODE_PRIVATE) }
    var opt by remember {
        mutableStateOf(
            StoryOptions(
                count = prefs.getBoolean("count", true),
                tier = prefs.getBoolean("tier", false),
                setup = prefs.getBoolean("setup", true),
                line = prefs.getBoolean("line", true),
            ),
        )
    }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(opt) {
        prefs.edit().putBoolean("count", opt.count).putBoolean("tier", opt.tier).putBoolean("setup", opt.setup).putBoolean("line", opt.line).apply()
        bmp = Story.render(ctx, t, w, opt)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Share to your story", style = MaterialTheme.typography.titleLarge)
            Text(
                "Your thoughts and scores stay private.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.align(Alignment.CenterHorizontally).height(420.dp).aspectRatio(9f / 16f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b == null) LoadingIndicator()
                    else Image(b.asImageBitmap(), contentDescription = "Story card", contentScale = ContentScale.Fit, modifier = Modifier.clip(RoundedCornerShape(20.dp)))
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Chip(opt.count, { opt = opt.copy(count = !opt.count) }) { Text("${t.kind.label} number") }
                if (w.tier != null) Chip(opt.tier, { opt = opt.copy(tier = !opt.tier) }) { Text("Verdict") }
                if (w.setup.isNotEmpty()) Chip(opt.setup, { opt = opt.copy(setup = !opt.setup) }) { Text("Setup") }
                if (w.line.isNotBlank()) Chip(opt.line, { opt = opt.copy(line = !opt.line) }) { Text("Your line") }
            }
            Spacer(Modifier.height(16.dp))
            val activity = ctx as? Activity
            Button(
                onClick = { val b = bmp; if (b != null && activity != null) scope.launch { Story.toInstagram(activity, b) } },
                enabled = bmp != null && activity != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Instagram story") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { val b = bmp; if (b != null && activity != null) scope.launch { Story.share(activity, b) } },
                    enabled = bmp != null && activity != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                }
                FilledTonalButton(
                    onClick = {
                        val b = bmp
                        if (b != null) scope.launch { nav.toast(if (Story.save(ctx, b, "${t.name} ${w.date}")) "Saved to Pictures/Mise" else "Couldn't save") }
                    },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save")
                }
            }
        }
    }
}
