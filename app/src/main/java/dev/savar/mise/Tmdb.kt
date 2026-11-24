package dev.savar.mise

import org.json.JSONObject
import java.net.URLEncoder

/**
 * TMDB (themoviedb.org): search and details for films and shows. Works with either credential TMDB gives you:
 * the long "API Read Access Token" (sent as a Bearer header) or the 32-character v3 "API Key" (sent as api_key).
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"

    data class Hit(val kind: Kind, val tmdbId: Long, val name: String, val year: Int?, val poster: String, val overview: String) {
        val id get() = idFor(kind, tmdbId)
    }

    fun idFor(kind: Kind, tmdbId: Long) = (if (kind == Kind.MOVIE) "movie:" else "tv:") + tmdbId

    fun image(path: String, size: String = "w342"): String =
        if (path.isBlank()) "" else "https://image.tmdb.org/t/p/$size$path"

    private fun isV3Key(token: String) = token.length == 32 && token.all { it.isLetterOrDigit() }

    private fun call(token: String, path: String, query: Map<String, String> = emptyMap()): JSONObject {
        if (token.isBlank()) throw IllegalStateException("Add a TMDB token in Settings")
        val q = query.toMutableMap()
        val headers = mutableMapOf<String, String>()
        if (isV3Key(token)) q["api_key"] = token else headers["Authorization"] = "Bearer $token"
        val qs = q.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return JSONObject(Http.get("$API$path" + if (qs.isEmpty()) "" else "?$qs", headers))
    }

    fun search(token: String, query: String): List<Hit> {
        val o = call(token, "/search/multi", mapOf("query" to query, "include_adult" to "false"))
        val arr = o.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.getJSONObject(i)
            val kind = when (r.optString("media_type")) {
                "movie" -> Kind.MOVIE
                "tv" -> Kind.TV
                else -> return@mapNotNull null
            }
            Hit(
                kind = kind,
                tmdbId = r.getLong("id"),
                name = r.optString(if (kind == Kind.MOVIE) "title" else "name"),
                year = yearOf(r.optString(if (kind == Kind.MOVIE) "release_date" else "first_air_date")),
                poster = r.optString("poster_path").takeUnless { it == "null" } ?: "",
                overview = r.optString("overview"),
            )
        }
    }

    fun details(token: String, kind: Kind, tmdbId: Long): Title {
        return if (kind == Kind.MOVIE) {
            val o = call(token, "/movie/$tmdbId", mapOf("append_to_response" to "credits"))
            val crew = o.optJSONObject("credits")?.optJSONArray("crew")
            val directors = crew?.let { c -> (0 until c.length()).map { c.getJSONObject(it) }.filter { it.optString("job") == "Director" }.map { it.optString("name") } }
                ?: emptyList()
            Title(
                id = idFor(kind, tmdbId), kind = kind, tmdbId = tmdbId,
                name = o.optString("title"),
                year = yearOf(o.optString("release_date")),
                poster = o.str("poster_path"), backdrop = o.str("backdrop_path"),
                credit = directors.distinct().joinToString(", "),
                runtime = o.optInt("runtime").takeIf { it > 0 },
                genres = o.genres(),
                overview = o.optString("overview"),
            )
        } else {
            val o = call(token, "/tv/$tmdbId")
            val creators = o.optJSONArray("created_by")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
            val ep = o.optJSONArray("episode_run_time")?.let { a -> if (a.length() > 0) a.optInt(0) else 0 } ?: 0
            Title(
                id = idFor(kind, tmdbId), kind = kind, tmdbId = tmdbId,
                name = o.optString("name"),
                year = yearOf(o.optString("first_air_date")),
                poster = o.str("poster_path"), backdrop = o.str("backdrop_path"),
                credit = creators.joinToString(", "),
                runtime = ep.takeIf { it > 0 },
                seasons = o.optInt("number_of_seasons").takeIf { it > 0 },
                genres = o.genres(),
                overview = o.optString("overview"),
            )
        }
    }

    private fun JSONObject.str(k: String) = optString(k).takeUnless { it == "null" } ?: ""
    private fun JSONObject.genres(): List<String> =
        optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()

    internal fun yearOf(date: String): Int? = date.take(4).toIntOrNull()
}
