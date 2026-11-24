@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Log (or edit) one watch. The heart of it is "Talk about it": one tap records a voice memo and, on Android 13+,
 * writes what you say into Thoughts as you speak. Everything else is optional.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogScreen(titleId: String, watchId: String?, previewRecording: Boolean = false) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val t = Store.title(titleId)
    if (t == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val existing = remember { watchId?.let { Store.watch(it) } }
    var draft by remember { mutableStateOf(existing ?: Watch(titleId = titleId)) }
    val recordedHere = remember { mutableStateListOf<Memo>() }
    val removed = remember { mutableStateListOf<Memo>() }
    var datePicker by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val dirty = draft != (existing ?: Watch(id = draft.id, titleId = titleId, day = draft.day, createdAt = draft.createdAt, updatedAt = draft.updatedAt)) ||
        recordedHere.isNotEmpty() || removed.isNotEmpty()

    // ---------- voice ----------
    val recorder = remember { Recorder() }
    var transcriber by remember { mutableStateOf<Transcriber?>(null) }
    var partial by remember { mutableStateOf(if (previewRecording) "and the organ in the docking scene" else "") }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var transcribing by remember { mutableStateOf<String?>(null) } // memo file being transcribed
    val recording = recorder.recording || previewRecording

    fun appendThought(seg: String) {
        val s = seg.trim().replaceFirstChar { it.uppercase() }
        if (s.isEmpty()) return
        val cur = draft.thoughts.trimEnd()
        val joined = when {
            cur.isEmpty() -> s
            cur.endsWith('.') || cur.endsWith('!') || cur.endsWith('?') -> "$cur $s"
            else -> "$cur. $s"
        }
        draft = draft.copy(thoughts = joined)
    }

    fun newTranscriber(onDone: () -> Unit = {}): Transcriber = Transcriber(
        ctx, Store.settings.speechLang,
        onPartial = { partial = it },
        onSegment = { appendThought(it) },
        onDone = { err ->
            if (err != null) voiceNote = err
            transcriber = null
            onDone()
        },
    )

    fun startRecording() {
        Player.stop()
        voiceNote = null
        val f = File(Store.memoDir(ctx), UUID.randomUUID().toString() + ".m4a")
        val tr = if (Transcriber.supported(ctx)) newTranscriber() else null
        val live = tr?.start() == true
        transcriber = if (live) tr else null
        if (!live) voiceNote = "Live transcript isn't available on this phone. Your memo is still saved."
        if (!recorder.start(f) { b, n -> tr?.feed(b, n) }) {
            tr?.destroy()
            transcriber = null
            voiceNote = "Couldn't open the microphone"
            return
        }
        file = f
    }

    fun stopRecording() {
        val f = file ?: return
        file = null
        scope.launch {
            val ms = withContext(Dispatchers.IO) { recorder.stop() }
            transcriber?.finish()
            if (ms != null && ms >= 700) {
                val m = Memo(f.name, ms, System.currentTimeMillis())
                recordedHere += m
                draft = draft.copy(memos = draft.memos + m)
            } else {
                f.delete()
                if (ms == null) voiceNote = "Recording failed"
            }
        }
    }

    fun transcribe(m: Memo) {
        if (transcribing != null || recording) return
        val tr = newTranscriber { transcribing = null }
        if (!tr.start()) { voiceNote = "Transcription isn't available on this phone"; return }
        transcriber = tr
        transcribing = m.file
        scope.launch(Dispatchers.IO) {
            runCatching { Pcm.decode(Store.memoFile(ctx, m)) { b, n -> tr.feedWaiting(b, n) } }
            tr.finish()
        }
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else voiceNote = "Mise needs the microphone to record"
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.recording) {
                val f = file
                Thread { recorder.stop(); if (f != null && f.name !in draft.memos.map { it.file }) f.delete() }.start()
            }
            transcriber?.destroy()
            Player.stop()
        }
    }

    fun close(save: Boolean) {
        if (recorder.recording) stopRecording()
        if (save) {
            removed.forEach { Store.memoFile(ctx, it).delete() }
            Store.saveWatch(draft)
            nav.afterSave(titleId)
            nav.toast(if (existing == null) "Logged. Share it with the share button." else "Saved")
        } else {
            recordedHere.forEach { Store.memoFile(ctx, it).delete() }
            nav.back()
        }
    }

    BackHandler { if (dirty) discard = true else close(false) }

    Page(
        if (existing == null) "Log" else "Edit",
        onBack = { if (dirty) discard = true else close(false) },
        actions = { TextButton(onClick = { close(true) }, enabled = !recording) { Text("Save") } },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // What and when.
            Group {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(t, 44.dp, corner = 8.dp, showName = false)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.meta(t), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item { NavRow(Icons.Rounded.CalendarMonth, "Watched", Fmt.longDay(draft.date)) { datePicker = true } }
                if (t.kind == Kind.TV) {
                    item {
                        Pad {
                            OutlinedTextField(
                                draft.season, { draft = draft.copy(season = it) },
                                label = { Text("Season or episodes") },
                                placeholder = { Text("Season 2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Talk about it.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp).animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (recording) "Listening…" else "Talk about it",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (recording) Fmt.duration(recorder.elapsedMs) else "What worked, what didn't, how you felt walking out",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    val pulse by animateFloatAsState(if (recording) 1f + recorder.level * 0.35f else 1f, label = "pulse")
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                        if (recording) {
                            Box(Modifier.size(80.dp).scale(pulse * 1.18f).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
                        }
                        FilledIconButton(
                            onClick = {
                                when {
                                    recording -> stopRecording()
                                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startRecording()
                                    else -> mic.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = transcribing == null,
                            modifier = Modifier.size(80.dp),
                            colors = if (recording) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                            else IconButtonDefaults.filledIconButtonColors(),
                        ) {
                            Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) "Stop" else "Record", Modifier.size(36.dp))
                        }
                    }
                    if (partial.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(partial, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    voiceNote?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (draft.memos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            draft.memos.forEach { m ->
                                MemoRow(
                                    m,
                                    onDelete = if (recording) null else ({
                                        Player.stop()
                                        draft = draft.copy(memos = draft.memos - m)
                                        if (recordedHere.remove(m)) Store.memoFile(ctx, m).delete() else removed += m
                                    }),
                                    trailing = {
                                        if (Transcriber.supported(ctx) && !recording) {
                                            if (transcribing == m.file) {
                                                LoadingIndicator(Modifier.size(40.dp))
                                            } else {
                                                IconButton(onClick = { transcribe(m) }, enabled = transcribing == null) {
                                                    Icon(Icons.Rounded.Subtitles, "Transcribe into Thoughts")
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                draft.thoughts, { draft = draft.copy(thoughts = it) },
                label = { Text("Thoughts") },
                placeholder = { Text("Speak above, or type") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                draft.mood, { draft = draft.copy(mood = it) },
                label = { Text("State of mind") },
                placeholder = { Text("Wiped out after a long week, wanted something huge") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Verdict") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tier.entries.forEach { tier ->
                        Chip(draft.tier == tier, { draft = draft.copy(tier = if (draft.tier == tier) null else tier) }) { Text(tier.label) }
                    }
                }
            }

            Group("Scores · optional") {
                Axis.entries.forEach { a -> item { ScoreRow(a, draft.scores[a]) { v -> draft = draft.copy(scores = if (v == null) draft.scores - a else draft.scores + (a to v)) } } }
            }

            Section("How you watched") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Setup.entries.forEach { s ->
                        Chip(s in draft.setup, { draft = draft.copy(setup = if (s in draft.setup) draft.setup - s else draft.setup + s) }) { Text(s.label) }
                    }
                }
            }

            OutlinedTextField(
                draft.line, { draft = draft.copy(line = it.take(120)) },
                label = { Text("A line for your story · optional") },
                supportingText = { Text("The only words that ever leave the app. Your thoughts stay private.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = { close(true) }, enabled = !recording, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (existing == null) "Save to diary" else "Save")
            }
        }
    }

    if (datePicker) {
        val today = LocalDate.now().toEpochDay()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.day * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000L <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { draft = draft.copy(day = it / 86_400_000L) }
                    datePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard this log?") },
            text = { Text(if (recordedHere.isNotEmpty()) "The voice memos you just recorded will be deleted too." else "Your changes won't be saved.") },
            confirmButton = { TextButton(onClick = { discard = false; close(false) }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Box(Modifier.padding(horizontal = 4.dp)) { content() }
    }
}

/** 0–10 in halves. Untouched = no score (shown as "–"); the × clears it. */
@Composable
private fun ScoreRow(axis: Axis, value: Float?, onChange: (Float?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(axis.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                value?.let { Fmt.score(it) } ?: "–",
                style = MaterialTheme.typography.titleMedium,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { onChange(null) }, enabled = value != null) {
                Icon(Icons.Rounded.Close, "Clear ${axis.label}", Modifier.size(18.dp))
            }
        }
        Slider(
            value = value ?: 5f,
            onValueChange = { onChange((it * 2).let { x -> kotlin.math.round(x) } / 2f) },
            valueRange = 0f..10f,
            steps = 19,
            colors = if (value == null) SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.outline,
                activeTrackColor = MaterialTheme.colorScheme.outlineVariant,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) else SliderDefaults.colors(),
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}
