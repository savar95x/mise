package dev.savar.mise

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    fun day(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (ChronoUnit.DAYS.between(d, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        in 2L..6L -> DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(d)
        else -> DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "d MMM yyyy", Locale.getDefault()).format(d)
    }

    fun longDay(d: LocalDate): String = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()).format(d)

    fun month(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        DateTimeFormatter.ofPattern(if (d.year == today.year) "MMMM" else "MMMM yyyy", Locale.getDefault()).format(d)

    fun runtime(min: Int?): String? = min?.takeIf { it > 0 }?.let { if (it < 60) "${it}m" else "${it / 60}h ${it % 60}m" }

    fun duration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
        if (t <= 0) return "never"
        val m = (now - t) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 24 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} d ago"
        }
    }

    fun score(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

    /** "2014 · Christopher Nolan · 2h 49m" or "2021 · Christian Linke, Alex Yee · 2 seasons". */
    fun meta(t: Title): String = listOfNotNull(
        t.year?.toString(),
        t.credit.takeIf { it.isNotBlank() },
        if (t.kind == Kind.TV) t.seasons?.let { "$it season" + if (it == 1) "" else "s" } else runtime(t.runtime),
    ).joinToString(" · ")
}
