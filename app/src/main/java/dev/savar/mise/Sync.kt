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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Text-only sync through your own Supabase project: titles, watchlist and watches (thoughts, scores, mood…) go up;
 * voice memos stay on the phone. One table, one row per item, last write wins per item.
 *
 * Push: every item changed since the last push (upsert). Pull: every row the server touched since the last pull,
 * by the server's own `synced_at` clock. A trigger keeps the newer `updated_at` if two devices race.
 */
object Sync {
    data class Config(
        val url: String = "",
        val key: String = "", // anon / publishable key
        val email: String = "",
        val userId: String = "",
        val access: String = "",
        val refresh: String = "",
        val expiresAt: Long = 0,
    ) {
        val configured get() = url.isNotBlank() && key.isNotBlank()
        val signedIn get() = configured && refresh.isNotBlank()
    }

    var config by mutableStateOf(Config())
        private set
    var busy by mutableStateOf(false)
        private set
    var lastSync by mutableStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var soonJob: Job? = null
    private var loaded = false

    const val SETUP_SQL = """create table if not exists public.mise_items (
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  id text not null,
  type text not null,
  data jsonb not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  synced_at timestamptz not null default now(),
  primary key (user_id, id)
);
create index if not exists mise_items_synced on public.mise_items (user_id, synced_at);
alter table public.mise_items enable row level security;
drop policy if exists "own rows" on public.mise_items;
create policy "own rows" on public.mise_items for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create or replace function public.mise_touch() returns trigger language plpgsql as $$
begin
  if tg_op = 'UPDATE' and new.updated_at < old.updated_at then return null; end if;
  new.synced_at = clock_timestamp();
  return new;
end $$;
drop trigger if exists mise_touch on public.mise_items;
create trigger mise_touch before insert or update on public.mise_items
  for each row execute function public.mise_touch();"""

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        config = Config(
            url = p.getString("url", "") ?: "",
            key = p.getString("key", "") ?: "",
            email = p.getString("email", "") ?: "",
            userId = p.getString("user", "") ?: "",
            access = p.getString("access", "") ?: "",
            refresh = p.getString("refresh", "") ?: "",
            expiresAt = p.getLong("expires", 0),
        )
        lastSync = p.getLong("last_sync", 0)
    }

    private fun saveConfig(ctx: Context, c: Config) {
        config = c
        prefs(ctx).edit()
            .putString("url", c.url).putString("key", c.key).putString("email", c.email).putString("user", c.userId)
            .putString("access", c.access).putString("refresh", c.refresh).putLong("expires", c.expiresAt)
            .apply()
    }

    fun setProject(ctx: Context, url: String, key: String) {
        val clean = url.trim().trimEnd('/').let { if (it.isNotBlank() && !it.startsWith("http")) "https://$it" else it }
        val changed = clean != config.url
        saveConfig(ctx, config.copy(url = clean, key = key.trim()).let { if (changed) it.copy(access = "", refresh = "", userId = "") else it })
        if (changed) resetCursors(ctx)
    }

    private fun resetCursors(ctx: Context) {
        prefs(ctx).edit().remove("pull_cursor").remove("pushed_until").remove("last_sync").apply()
        lastSync = 0
    }

    // ---------- auth (Supabase Auth, email + password) ----------

    private fun headers(c: Config, bearer: String? = null) = mapOf(
        "apikey" to c.key,
        "Authorization" to "Bearer ${bearer ?: c.key}",
    )

    private fun session(ctx: Context, c: Config, email: String, o: JSONObject): Config {
        val next = c.copy(
            email = email,
            userId = o.optJSONObject("user")?.optString("id") ?: c.userId,
            access = o.optString("access_token"),
            refresh = o.optString("refresh_token"),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
        )
        saveConfig(ctx, next)
        return next
    }

    /** Signs in, or with [create] signs up first. Returns a message for the UI. */
    suspend fun signIn(ctx: Context, email: String, password: String, create: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val c = config
        runCatching {
            if (create) {
                val o = JSONObject(Http.send("POST", "${c.url}/auth/v1/signup", JSONObject().put("email", email).put("password", password).toString(), headers(c)))
                if (o.optString("access_token").isBlank()) {
                    return@runCatching "Check your email to confirm the account, then sign in"
                }
                session(ctx, c, email, o)
            } else {
                val o = JSONObject(Http.send("POST", "${c.url}/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password).toString(), headers(c)))
                session(ctx, c, email, o)
            }
            resetCursors(ctx)
            syncNow(ctx)
            "Signed in"
        }.recoverCatching { throw Exception(describe(it)) }
    }

    fun signOut(ctx: Context) {
        saveConfig(ctx, config.copy(access = "", refresh = "", userId = ""))
        resetCursors(ctx)
        error = null
    }

    private fun token(ctx: Context): String {
        val c = config
        if (c.access.isNotBlank() && System.currentTimeMillis() < c.expiresAt - 60_000) return c.access
        val o = JSONObject(Http.send("POST", "${c.url}/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", c.refresh).toString(), headers(c)))
        return session(ctx, c, c.email, o).access
    }

    // ---------- sync ----------

    /** A few seconds after an edit (edits come in bursts). */
    fun soon(ctx: Context) {
        if (!config.signedIn) return
        soonJob?.cancel()
        soonJob = scope.launch { delay(3_000); runSync(ctx.applicationContext) }
    }

    fun syncIfStale(ctx: Context) {
        load(ctx)
        if (config.signedIn && System.currentTimeMillis() - lastSync > 2 * 60_000L) syncNow(ctx)
    }

    fun syncNow(ctx: Context) {
        if (!config.signedIn) return
        scope.launch { runSync(ctx.applicationContext) }
    }

    private suspend fun runSync(ctx: Context) {
        lock.withLock {
            withContext(Dispatchers.Main) { busy = true }
            val r = runCatching { doSync(ctx) }
            withContext(Dispatchers.Main) {
                busy = false
                error = r.exceptionOrNull()?.let { describe(it) }
                if (r.isSuccess) {
                    lastSync = System.currentTimeMillis()
                    prefs(ctx).edit().putLong("last_sync", lastSync).apply()
                }
            }
        }
    }

    private fun doSync(ctx: Context) {
        val c = config
        val auth = headers(c, token(ctx))
        val p = prefs(ctx)

        // Pull first, so a stale local copy doesn't overwrite newer rows (the trigger would refuse it anyway).
        var cursor = p.getString("pull_cursor", "") ?: ""
        val titles = mutableListOf<Title>()
        val watches = mutableListOf<Watch>()
        while (true) {
            val filter = if (cursor.isBlank()) "" else "&synced_at=gt.${URLEncoder.encode(cursor, "UTF-8")}"
            val arr = JSONArray(Http.get("${c.url}/rest/v1/mise_items?select=id,type,data,synced_at$filter&order=synced_at.asc&limit=500", auth))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                val data = row.optJSONObject("data") ?: continue
                runCatching {
                    when (row.optString("type")) {
                        "title" -> titles += Title.fromJson(data)
                        "watch" -> watches += Watch.fromJson(data)
                    }
                }
                cursor = row.optString("synced_at", cursor)
            }
            if (arr.length() < 500) break
        }
        val pushStarted = System.currentTimeMillis()
        if (titles.isNotEmpty() || watches.isNotEmpty()) {
            kotlinx.coroutines.runBlocking(Dispatchers.Main) { Store.merge(titles, watches) }
        }
        p.edit().putString("pull_cursor", cursor).apply()

        // Push everything edited since the last push.
        val since = p.getLong("pushed_until", 0)
        val rows = JSONArray()
        Store.titles.values.filter { it.updatedAt > since }.forEach {
            rows.put(JSONObject().put("user_id", c.userId).put("id", "t:" + it.id).put("type", "title").put("data", it.toJson()).put("updated_at", it.updatedAt).put("deleted", it.deleted))
        }
        Store.watches.filter { it.updatedAt > since }.forEach {
            rows.put(JSONObject().put("user_id", c.userId).put("id", "w:" + it.id).put("type", "watch").put("data", it.toJson()).put("updated_at", it.updatedAt).put("deleted", it.deleted))
        }
        var i = 0
        while (i < rows.length()) {
            val chunk = JSONArray()
            for (j in i until minOf(i + 200, rows.length())) chunk.put(rows.get(j))
            Http.send(
                "POST",
                "${c.url}/rest/v1/mise_items?on_conflict=user_id,id",
                chunk.toString(),
                auth + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
            i += 200
        }
        p.edit().putLong("pushed_until", pushStarted).apply()
    }

    private fun describe(e: Throwable): String = when {
        e is HttpStatusException && e.body.contains("mise_items") && (e.code == 404 || e.body.contains("does not exist") || e.body.contains("schema cache")) ->
            "The mise_items table is missing: run the setup SQL"
        e is HttpStatusException && (e.code == 400 || e.code == 401) && e.body.contains("Invalid login", true) -> "Wrong email or password"
        e is HttpStatusException && e.body.contains("Email not confirmed", true) -> "Confirm your email first"
        e is HttpStatusException && (e.code == 401 || e.code == 403) -> "Signed out: sign in again"
        e is HttpStatusException && (e.code == 540 || e.code == 503) -> "The Supabase project is paused: restore it from the dashboard"
        e is HttpStatusException -> "Supabase answered ${e.code}" + (JSONObjectOrNull(e.body)?.let { o -> o.optString("msg").ifBlank { o.optString("message") }.takeIf { it.isNotBlank() }?.let { ": $it" } } ?: "")
        e is java.net.UnknownHostException -> "No connection, or the project URL is wrong"
        e is java.net.SocketTimeoutException -> "Supabase didn't answer in time"
        else -> e.message ?: "Sync failed"
    }

    @Suppress("FunctionName")
    private fun JSONObjectOrNull(s: String): JSONObject? = runCatching { JSONObject(s) }.getOrNull()
}
