package dev.savar.mise

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Settings(
    val tmdbToken: String = "",
    /** BCP-47 tag for transcription, "" = the phone's language. */
    val speechLang: String = "",
)

/**
 * All journal state as Compose state, saved to files/journal.json. Deletions are kept as tombstones
 * (`deleted = true`) so they reach other devices through sync; the UI never shows them.
 */
object Store {
    var titles by mutableStateOf<Map<String, Title>>(emptyMap())
        private set
    var watches by mutableStateOf<List<Watch>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set

    private var app: Context? = null
    private var loaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "journal.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun memoDir(ctx: Context) = File(ctx.filesDir, "memos").apply { mkdirs() }
    fun memoFile(ctx: Context, m: Memo) = File(memoDir(ctx), m.file)

    fun load(ctx: Context) {
        app = ctx.applicationContext
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        settings = Settings(tmdbToken = p.getString("tmdb", "") ?: "", speechLang = p.getString("speech_lang", "") ?: "")
        val f = file(ctx)
        if (f.exists()) runCatching { read(f.readText()) }.onSuccess { (t, w) ->
            titles = t.associateBy { it.id }
            watches = w
        }
    }

    internal fun read(text: String): Pair<List<Title>, List<Watch>> {
        val o = JSONObject(text)
        val t = o.optJSONArray("titles")?.let { a -> (0 until a.length()).mapNotNull { runCatching { Title.fromJson(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
        val w = o.optJSONArray("watches")?.let { a -> (0 until a.length()).mapNotNull { runCatching { Watch.fromJson(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
        return t to w
    }

    fun json(): String = JSONObject()
        .put("version", 1)
        .put("titles", JSONArray(titles.values.map { it.toJson() }))
        .put("watches", JSONArray(watches.map { it.toJson() }))
        .toString()

    /** Saves shortly after the last change, then lets sync push it. */
    private fun changed() {
        val ctx = app ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            writeNow(ctx)
            Sync.soon(ctx)
        }
    }

    @Synchronized
    fun writeNow(ctx: Context) {
        val f = file(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json())
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    // ---------- reads ----------

    fun title(id: String): Title? = titles[id]?.takeUnless { it.deleted }

    /** Every live watch, newest first. */
    fun diary(): List<Watch> = watches.filter { !it.deleted && titles[it.titleId]?.deleted == false }
        .sortedWith(compareByDescending<Watch> { it.day }.thenByDescending { it.createdAt })

    fun watchesOf(titleId: String): List<Watch> = diary().filter { it.titleId == titleId }

    fun watchlist(): List<Title> = titles.values.filter { !it.deleted && it.onWatchlist }.sortedByDescending { it.watchlistAt }

    fun watch(id: String): Watch? = watches.firstOrNull { it.id == id && !it.deleted }

    /** 3 for "Film #3 of 2026": this watch's place among the year's watches of the same kind. */
    fun yearNumber(w: Watch): Int {
        val kind = titles[w.titleId]?.kind ?: return 0
        val year = w.date.year
        return diary().filter { titles[it.titleId]?.kind == kind && it.date.year == year }
            .sortedWith(compareBy<Watch> { it.day }.thenBy { it.createdAt })
            .indexOfFirst { it.id == w.id } + 1
    }

    /** Is this an earlier-watched title being watched again? */
    fun isRewatch(w: Watch): Boolean =
        diary().any { it.titleId == w.titleId && it.id != w.id && (it.day < w.day || (it.day == w.day && it.createdAt < w.createdAt)) }

    fun countThisYear(kind: Kind, year: Int = LocalDate.now().year): Int =
        diary().count { titles[it.titleId]?.kind == kind && it.date.year == year }

    // ---------- writes ----------

    /** Adds or refreshes a title's details, keeping its watchlist state. */
    fun putTitle(t: Title) {
        val old = titles[t.id]
        val merged = t.copy(
            watchlistAt = if (old != null && !old.deleted) old.watchlistAt else t.watchlistAt,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
        )
        titles = titles + (t.id to merged)
        changed()
    }

    fun setWatchlist(id: String, on: Boolean) {
        val t = titles[id] ?: return
        if (t.onWatchlist == on) return
        titles = titles + (id to t.copy(watchlistAt = if (on) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()))
        changed()
    }

    fun saveWatch(w: Watch) {
        val now = System.currentTimeMillis()
        val saved = w.copy(updatedAt = now)
        watches = watches.filter { it.id != w.id } + saved
        // Logged it: it's watched, so it leaves the watchlist.
        titles[w.titleId]?.takeIf { it.onWatchlist }?.let {
            titles = titles + (it.id to it.copy(watchlistAt = null, updatedAt = now))
        }
        changed()
    }

    fun deleteWatch(id: String) {
        val ctx = app
        val w = watches.firstOrNull { it.id == id } ?: return
        if (ctx != null) w.memos.forEach { memoFile(ctx, it).delete() }
        watches = watches.map { if (it.id == id) it.copy(deleted = true, memos = emptyList(), updatedAt = System.currentTimeMillis()) else it }
        changed()
    }

    /** Removes the title and every watch of it. */
    fun deleteTitle(id: String) {
        watches.filter { it.titleId == id && !it.deleted }.forEach { deleteWatch(it.id) }
        titles[id]?.let { titles = titles + (id to it.copy(deleted = true, watchlistAt = null, updatedAt = System.currentTimeMillis())) }
        changed()
    }

    fun updateSettings(ctx: Context, f: (Settings) -> Settings) {
        settings = f(settings)
        prefs(ctx).edit().putString("tmdb", settings.tmdbToken).putString("speech_lang", settings.speechLang).apply()
    }

    /**
     * Last write wins, per item. Memos are merged rather than replaced: their audio only exists on the phone
     * that recorded it, so a newer edit from another device must not drop them.
     */
    fun merge(remoteTitles: List<Title>, remoteWatches: List<Watch>): Int {
        var n = 0
        val t = titles.toMutableMap()
        for (r in remoteTitles) {
            val l = t[r.id]
            if (l == null || r.updatedAt > l.updatedAt) { t[r.id] = r; n++ }
        }
        val w = watches.associateBy { it.id }.toMutableMap()
        for (r in remoteWatches) {
            val l = w[r.id]
            if (l == null || r.updatedAt > l.updatedAt) {
                val memos = if (l == null || r.deleted) r.memos else (r.memos + l.memos).distinctBy { it.file }
                w[r.id] = r.copy(memos = memos)
                n++
            }
        }
        if (n > 0) {
            titles = t
            watches = w.values.toList()
            changed()
        }
        return n
    }

    /** Screenshots and tests only. */
    fun seed(t: List<Title>, w: List<Watch>, s: Settings = Settings()) {
        loaded = true
        titles = t.associateBy { it.id }
        watches = w
        settings = s
    }
}
