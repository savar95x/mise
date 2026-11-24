package dev.savar.mise

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What goes on the card besides the film itself. The review never does, unless you write a line for it. */
data class StoryOptions(
    val count: Boolean = true,
    val tier: Boolean = false,
    val setup: Boolean = true,
    val line: Boolean = true,
)

/**
 * The Instagram story card (1080×1920): the backdrop blurred behind, the poster, "WATCHED", the title, who made
 * it, and a quiet footer ("Film #47 of 2026", the setup). Drawn with the platform canvas so it's identical on
 * every phone, in the family's colours (cream + coral on near-black) and the app font.
 */
object Story {
    const val W = 1080
    const val H = 1920
    private const val CREAM = 0xFFEEE7DC.toInt()
    private const val CORAL = 0xFFE2957A.toInt()
    private const val INK = 0xFF0F0F11.toInt()

    suspend fun render(ctx: Context, title: Title, watch: Watch, opt: StoryOptions): Bitmap = withContext(Dispatchers.IO) {
        val poster = Images.load(ctx, Tmdb.image(title.poster, "w780"), 600)
        val back = Images.load(ctx, Tmdb.image(title.backdrop, "w780"), 400) ?: poster
        val regular = Fonts.typeface(ctx, 400) ?: Typeface.create(Typeface.DEFAULT, 400, false)
        val medium = Fonts.typeface(ctx, 500) ?: Typeface.create(Typeface.DEFAULT, 500, false)
        val bold = Fonts.typeface(ctx, 700) ?: Typeface.create(Typeface.DEFAULT, 700, false)

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(INK)

        // Background: the backdrop, blurred by shrinking and stretching it back, then darkened.
        if (back != null) {
            val small = Bitmap.createScaledBitmap(back, (back.width / 14).coerceAtLeast(2), (back.height / 14).coerceAtLeast(2), true)
            val tiny = Bitmap.createScaledBitmap(small, (small.width / 2).coerceAtLeast(2), (small.height / 2).coerceAtLeast(2), true)
            drawCover(c, tiny, RectF(0f, 0f, W.toFloat(), H.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0xA60F0F11.toInt(), 0xCC0F0F11.toInt(), 0xF20F0F11.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            })
        }

        // Poster, with a soft shadow.
        val pw = 560f
        val ph = 840f
        val px = (W - pw) / 2
        val py = 232f
        val rect = RectF(px, py, px + pw, py + ph)
        val radius = 34f
        c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            setShadowLayer(48f, 0f, 18f, 0x99000000.toInt())
        })
        if (poster != null) {
            val shader = BitmapShader(poster, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(pw / poster.width, ph / poster.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(px + (pw - poster.width * scale) / 2, py + (ph - poster.height * scale) / 2)
            })
            c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        } else {
            c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF26262A.toInt() })
            text(c, title.name, bold, 64f, CREAM, rect.left + 48, rect.centerY() - 80, (pw - 96).toInt(), 4, Layout.Alignment.ALIGN_CENTER)
        }

        // Label: WATCHED / REWATCHED, and the season for shows.
        val rewatch = Store.isRewatch(watch)
        val label = buildString {
            append(if (rewatch) "REWATCHED" else "WATCHED")
            if (title.kind == Kind.TV && watch.season.isNotBlank()) append(" · ").append(watch.season.uppercase(Locale.getDefault()))
        }
        var y = rect.bottom + 92f
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 32f; color = CORAL; letterSpacing = 0.28f; textAlign = Paint.Align.CENTER
        }
        c.drawText(label, W / 2f, y, labelPaint)
        y += 34f

        // Title (two lines at most) and the line under it.
        y += text(c, title.name, bold, if (title.name.length > 22) 68f else 84f, CREAM, 90f, y, W - 180, 2, Layout.Alignment.ALIGN_CENTER)
        val meta = listOfNotNull(title.year?.toString(), title.credit.split(", ").take(2).joinToString(" & ").takeIf { it.isNotBlank() }).joinToString("  ·  ")
        if (meta.isNotBlank()) {
            y += 14f
            y += text(c, meta, regular, 38f, withAlpha(CREAM, 0.72f), 90f, y, W - 180, 1, Layout.Alignment.ALIGN_CENTER)
        }

        // Optional tier pill.
        val tier = watch.tier
        if (opt.tier && tier != null) {
            y += 40f
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textSize = 34f; color = CORAL }
            val tw = p.measureText(tier.label)
            val pill = RectF(W / 2f - tw / 2 - 30, y, W / 2f + tw / 2 + 30, y + 64)
            c.drawRoundRect(pill, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = CORAL })
            c.drawText(tier.label, pill.left + 30, pill.centerY() - (p.descent() + p.ascent()) / 2, p)
            y += 64f
        }

        // Footer, anchored to the bottom (above Instagram's reply bar).
        val footer = mutableListOf<String>()
        if (opt.count) {
            val n = Store.yearNumber(watch)
            if (n > 0) footer += "${title.kind.label} #$n of ${watch.date.year}"
        }
        footer += DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(watch.date)
        val setup = if (opt.setup) watch.setup.sortedBy { it.ordinal }.joinToString("  ·  ") { it.label } else ""
        var fy = H - 236f
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textSize = 36f; color = CREAM; textAlign = Paint.Align.CENTER }
        c.drawText(footer.joinToString("   ·   "), W / 2f, fy, fp)
        if (setup.isNotBlank()) {
            fy -= 60f
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = 32f; color = withAlpha(CREAM, 0.6f); textAlign = Paint.Align.CENTER }
            c.drawText(TextUtils.ellipsize(setup, sp, W - 160f, TextUtils.TruncateAt.END).toString(), W / 2f, fy, sp)
        }
        // A short coral rule between the film and the footer.
        val ruleY = fy - 78f
        c.drawRoundRect(RectF(W / 2f - 28, ruleY, W / 2f + 28, ruleY + 5), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CORAL })

        // Your line, if it fits between the title block and the rule.
        if (opt.line && watch.line.isNotBlank()) {
            val room = ruleY - 48f - (y + 52f)
            if (room > 60f) {
                text(c, "“${watch.line.trim()}”", regular, 42f, withAlpha(CREAM, 0.92f), 110f, y + 52f, W - 220, (room / 58f).toInt().coerceIn(1, 4), Layout.Alignment.ALIGN_CENTER)
            }
        }
        bmp
    }

    /** Draws wrapped text at (x, top) and returns its height. */
    private fun text(c: Canvas, s: String, tf: Typeface, size: Float, color: Int, x: Float, top: Float, width: Int, maxLines: Int, align: Layout.Alignment): Float {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color }
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width)
            .setAlignment(align)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.08f)
            .build()
        c.save()
        c.translate(x, top)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    private fun drawCover(c: Canvas, b: Bitmap, dst: RectF, paint: Paint) {
        val scale = maxOf(dst.width() / b.width, dst.height() / b.height)
        val w = b.width * scale
        val h = b.height * scale
        c.drawBitmap(b, null, RectF(dst.centerX() - w / 2, dst.centerY() - h / 2, dst.centerX() + w / 2, dst.centerY() + h / 2), paint)
    }

    private fun withAlpha(color: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    // ---------- out ----------

    private fun shareFile(ctx: Context, bmp: Bitmap): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "mise-story.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    /** Straight into an Instagram story when Instagram accepts it, otherwise the share sheet. */
    suspend fun toInstagram(activity: Activity, bmp: Bitmap) {
        val uri = withContext(Dispatchers.IO) { shareFile(activity, bmp) }
        val ig = "com.instagram.android"
        val intent = Intent("com.instagram.share.ADD_TO_STORY")
            .setDataAndType(uri, "image/png")
            .setPackage(ig)
            .putExtra("source_application", activity.packageName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.grantUriPermission(ig, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val ok = activity.packageManager.resolveActivity(intent, 0) != null &&
            runCatching { activity.startActivity(intent) }.isSuccess
        if (!ok) share(activity, uri)
    }

    suspend fun share(activity: Activity, bmp: Bitmap) {
        val uri = withContext(Dispatchers.IO) { shareFile(activity, bmp) }
        share(activity, uri)
    }

    private fun share(activity: Activity, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(Intent.createChooser(send, null)) }
    }

    /** Saves to Pictures/Mise. */
    suspend fun save(ctx: Context, bmp: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Mise" } + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mise")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
    }
}
