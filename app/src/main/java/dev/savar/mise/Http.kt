package dev.savar.mise

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpStatusException(val code: Int, val body: String = "") : IOException("HTTP $code")

/** Plain HttpURLConnection: a few small JSON calls and poster downloads, no need for a client library. */
object Http {
    private const val UA = "Mise/1.0 (Android; personal watch journal)"

    private fun open(url: String, headers: Map<String, String>, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = method
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, */*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun HttpURLConnection.readBody(): String {
        val ok = responseCode in 200..299
        val stream = if (ok) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (!ok) throw HttpStatusException(responseCode, text)
        return text
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = open(url, headers)
        try {
            return c.readBody()
        } finally {
            c.disconnect()
        }
    }

    /** POST / PATCH / DELETE with a JSON body. */
    fun send(method: String, url: String, body: String?, headers: Map<String, String> = emptyMap()): String {
        val c = open(url, headers, method)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            return c.readBody()
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, dest: File) {
        val c = open(url, emptyMap())
        try {
            if (c.responseCode != 200) throw HttpStatusException(c.responseCode)
            val tmp = File(dest.parentFile, dest.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            c.disconnect()
        }
    }
}
