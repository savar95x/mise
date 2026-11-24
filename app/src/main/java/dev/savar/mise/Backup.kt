package dev.savar.mise

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A single .zip with everything: journal.json plus every voice memo. Sync carries the text; this is how the audio
 * survives a new phone. Importing merges (newer edits win), it never wipes.
 */
object Backup {
    fun fileName(): String = "Mise-" + java.time.LocalDate.now() + ".zip"

    suspend fun export(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Store.writeNow(ctx)
            var memos = 0
            ctx.contentResolver.openOutputStream(uri)!!.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("journal.json"))
                    zip.write(Store.json().toByteArray())
                    zip.closeEntry()
                    Store.memoDir(ctx).listFiles()?.filter { it.isFile && it.extension == "m4a" }?.forEach { f ->
                        zip.putNextEntry(ZipEntry("memos/" + f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        memos++
                    }
                }
            }
            val n = Store.diary().size
            "Saved $n ${if (n == 1) "watch" else "watches"} and $memos voice ${if (memos == 1) "memo" else "memos"}"
        }
    }

    suspend fun import(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var json: String? = null
            var memos = 0
            val dir = Store.memoDir(ctx)
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == "journal.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                            e.name.startsWith("memos/") && e.name.endsWith(".m4a") -> {
                                val f = File(dir, File(e.name).name) // flatten: no paths from the archive
                                if (!f.exists()) { f.outputStream().use { zip.copyTo(it) }; memos++ }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val text = json ?: throw Exception("Not a Mise backup")
            val (t, w) = Store.read(text)
            val changed = withContext(Dispatchers.Main) { Store.merge(t, w) }
            "Imported $changed ${if (changed == 1) "change" else "changes"} and $memos voice ${if (memos == 1) "memo" else "memos"}"
        }
    }
}
