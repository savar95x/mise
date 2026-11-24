package dev.savar.mise

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class Kind(val label: String, val plural: String) { MOVIE("Film", "Films"), TV("Show", "Shows") }

/**
 * A verdict that isn't a number. "Great in its lane" is how a niche film can be loved without being put next to
 * Interstellar; "All-time" is kept for the ones that are.
 */
enum class Tier(val label: String) {
    ALL_TIME("All-time"),
    GREAT("Great in its lane"),
    GOOD("Good"),
    FINE("Fine"),
    MISS("Not for me"),
}

/** Separate scores, each optional, instead of one lossy rating. */
enum class Axis(val label: String) {
    STORY("Story"),
    DIRECTION("Direction"),
    PERFORMANCES("Performances"),
    SOUND("Sound & score"),
    VISUALS("Visuals"),
    ENGAGEMENT("Engagement"),
}

/** How it was watched. */
enum class Setup(val label: String) {
    UHD("4K"),
    HDR("HDR"),
    SURROUND("Surround"),
    HEADPHONES("Headphones"),
    DARK("Dark room"),
    CINEMA("Cinema"),
    ALONE("Alone"),
    COMPANY("With people"),
}

data class Title(
    val id: String, // "movie:157336", "tv:94605", or "custom:<uuid>"
    val kind: Kind,
    val name: String,
    val year: Int? = null,
    val tmdbId: Long? = null,
    val poster: String = "", // TMDB image path, "/abc.jpg"
    val backdrop: String = "",
    val credit: String = "", // director(s) for films, creator(s) for shows
    val runtime: Int? = null, // minutes; for shows, per episode
    val seasons: Int? = null,
    val genres: List<String> = emptyList(),
    val overview: String = "",
    val watchlistAt: Long? = null, // on the watchlist since
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val onWatchlist get() = watchlistAt != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("name", name).putOpt("year", year).putOpt("tmdbId", tmdbId)
        .put("poster", poster).put("backdrop", backdrop).put("credit", credit).putOpt("runtime", runtime)
        .putOpt("seasons", seasons).put("genres", JSONArray(genres)).put("overview", overview)
        .putOpt("watchlistAt", watchlistAt).put("updatedAt", updatedAt).put("deleted", deleted)

    companion object {
        fun fromJson(o: JSONObject) = Title(
            id = o.getString("id"),
            kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.MOVIE),
            name = o.optString("name"),
            year = o.optIntOrNull("year"),
            tmdbId = if (o.has("tmdbId") && !o.isNull("tmdbId")) o.getLong("tmdbId") else null,
            poster = o.optString("poster"),
            backdrop = o.optString("backdrop"),
            credit = o.optString("credit"),
            runtime = o.optIntOrNull("runtime"),
            seasons = o.optIntOrNull("seasons"),
            genres = o.optJSONArray("genres")?.strings() ?: emptyList(),
            overview = o.optString("overview"),
            watchlistAt = if (o.has("watchlistAt") && !o.isNull("watchlistAt")) o.getLong("watchlistAt") else null,
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
        )

        fun customId() = "custom:" + UUID.randomUUID().toString()
    }
}

/** A voice memo. The audio stays on the phone (files/memos/[file]); only this metadata syncs. */
data class Memo(val file: String, val durationMs: Long, val createdAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("file", file).put("durationMs", durationMs).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Memo(o.getString("file"), o.optLong("durationMs"), o.optLong("createdAt"))
    }
}

/** One sitting (or one stretch of a show). Watching again is a new Watch, so earlier thoughts are never lost. */
data class Watch(
    val id: String = UUID.randomUUID().toString(),
    val titleId: String,
    val day: Long = LocalDate.now().toEpochDay(), // LocalDate.toEpochDay()
    val season: String = "", // shows: "Season 2", "S1–S2"…
    val tier: Tier? = null,
    val scores: Map<Axis, Float> = emptyMap(),
    val setup: Set<Setup> = emptySet(),
    val mood: String = "", // state of mind
    val thoughts: String = "", // transcript + typing
    val line: String = "", // optional public line for the story card
    val memos: List<Memo> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("titleId", titleId).put("day", day).put("season", season).putOpt("tier", tier?.name)
        .put("scores", JSONObject().apply { scores.forEach { (k, v) -> put(k.name, v.toDouble()) } })
        .put("setup", JSONArray(setup.map { it.name })).put("mood", mood).put("thoughts", thoughts).put("line", line)
        .put("memos", JSONArray(memos.map { it.toJson() })).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("deleted", deleted)

    companion object {
        fun fromJson(o: JSONObject) = Watch(
            id = o.getString("id"),
            titleId = o.getString("titleId"),
            day = o.optLong("day", LocalDate.now().toEpochDay()),
            season = o.optString("season"),
            tier = o.optString("tier").takeIf { it.isNotBlank() }?.let { n -> Tier.entries.firstOrNull { it.name == n } },
            scores = o.optJSONObject("scores")?.let { s ->
                s.keys().asSequence().mapNotNull { k ->
                    Axis.entries.firstOrNull { it.name == k }?.let { it to s.getDouble(k).toFloat() }
                }.toMap()
            } ?: emptyMap(),
            setup = o.optJSONArray("setup")?.strings()?.mapNotNull { n -> Setup.entries.firstOrNull { it.name == n } }?.toSet()
                ?: emptySet(),
            mood = o.optString("mood"),
            thoughts = o.optString("thoughts"),
            line = o.optString("line"),
            memos = o.optJSONArray("memos")?.let { a -> (0 until a.length()).map { Memo.fromJson(a.getJSONObject(it)) } }
                ?: emptyList(),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
        )
    }
}

internal fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

internal fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(f) }
