@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Poster / backdrop cache: downloaded once to disk, decoded at the size shown, kept in memory. */
object Images {
    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(4)
    private val failed = HashMap<String, Long>()
    private const val RETRY_MS = 10 * 60_000L

    private fun file(ctx: Context, url: String) = File(File(ctx.cacheDir, "images").apply { mkdirs() }, "${url.hashCode().toUInt()}.img")

    /** Screenshot tests: no network. */
    @Volatile var offline = false

    fun cached(url: String, px: Int): Bitmap? = mem.get("$url@$px")

    suspend fun load(ctx: Context, url: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || offline) return@withContext null
        val key = "$url@$px"
        mem.get(key)?.let { return@withContext it }
        val failedAt = synchronized(failed) { failed[url] }
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) return@withContext null
        val f = file(ctx, url)
        var bmp = if (f.exists() && f.length() > 0) runCatching { decode(f, px) }.getOrNull() else null
        if (bmp == null) {
            f.delete()
            gate.withPermit { runCatching { Http.download(url, f) } }
            bmp = runCatching { decode(f, px) }.getOrNull()
            if (bmp == null) f.delete()
        }
        synchronized(failed) { if (bmp == null) failed[url] = System.currentTimeMillis() else failed.remove(url) }
        if (bmp != null) mem.put(key, bmp)
        bmp
    }

    /** Decodes with the largest power-of-two subsampling that keeps the width at least [px]. */
    private fun decode(f: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
private fun rememberImage(url: String, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val image by produceState(Images.cached(url, px)?.asImageBitmap(), url, px) {
        value = Images.cached(url, px)?.asImageBitmap()
        if (value == null && !inPreview && url.isNotBlank()) value = Images.load(ctx, url, px)?.asImageBitmap()
    }
    return image
}

/** 2:3 poster with rounded corners; a tonal tile with the title while loading or if there's none. */
@Composable
fun Poster(title: Title?, width: Dp, modifier: Modifier = Modifier, corner: Dp = 10.dp, showName: Boolean = true) {
    val height = width * 1.5f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { width.roundToPx() }
    val size = when {
        px <= 160 -> "w185"
        px <= 320 -> "w342"
        else -> "w500"
    }
    val url = title?.poster?.let { Tmdb.image(it, size) } ?: ""
    key(url) {
        val img = rememberImage(url, px)
        Box(
            modifier.size(width, height).clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(img, label = "poster") { b ->
                if (b != null) {
                    Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (showName && width >= 72.dp && title != null) {
                            Text(
                                title.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                modifier = Modifier.padding(8.dp),
                            )
                        } else {
                            Icon(
                                if (title?.kind == Kind.TV) Icons.Rounded.Tv else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Wide backdrop (16:9), cropped to fill. Nothing drawn while loading. */
@Composable
fun Backdrop(title: Title, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val url = Tmdb.image(title.backdrop, "w1280")
    key(url) {
        val img = rememberImage(url, with(density) { 600.dp.roundToPx() }.coerceAtMost(1280))
        Crossfade(img, label = "backdrop", modifier = modifier) { b ->
            if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize())
        }
    }
}
