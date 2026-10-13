package io.github.zigorminsk.spoty.util

import java.util.Locale

/** Размер в байтах → «12.3 MB». */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

/** 3:05 или 1:02:03. */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Сводка длительности для альбомов: «1 ч 12 мин» / «45 мин». */
fun formatTotalDuration(ms: Long, hourLabel: String, minuteLabel: String): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "$h $hourLabel $m $minuteLabel"
        h > 0 -> "$h $hourLabel"
        else -> "${maxOf(m, 1)} $minuteLabel"
    }
}

/** 1 234 → «1,2 тыс.» (суффиксы передаются снаружи, чтобы функция оставалась чистой). */
fun compactCount(n: Long, thousand: String, million: String): String {
    fun one(v: Double, suffix: String): String {
        val s = String.format(Locale.getDefault(), "%.1f", v)
        val trimmed = if (s.endsWith(".0") || s.endsWith(",0")) s.dropLast(2) else s
        return "$trimmed $suffix"
    }
    return when {
        n >= 1_000_000L -> one(n / 1_000_000.0, million)
        n >= 1_000L -> one(n / 1_000.0, thousand)
        else -> n.toString()
    }
}

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Ключ для сравнения названий: без регистра и знаков препинания. */
fun normalizeKey(value: String?): String =
    (value ?: "").lowercase().replace(NON_WORD, " ").trim()

/** Ключ для удаления дублей треков из разных источников. */
fun trackDedupKey(artist: String?, title: String?): String {
    val cleanTitle = (title ?: "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .let { normalizeKey(it) }
    return normalizeKey(artist) + "|" + cleanTitle
}

/** Убирает опасные для Lucene-запроса символы. */
fun sanitizeQuery(raw: String): String =
    raw.replace(Regex("[+\\-&|!(){}\\[\\]^\"~*?:\\\\/]"), " ").replace(Regex("\\s+"), " ").trim()

/** Выбирает год из строки вида 2012-01-01T00:00:00Z / 2012. */
fun parseYear(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val m = Regex("(\\d{4})").find(raw) ?: return null
    val y = m.groupValues[1].toInt()
    return if (y in 1000..2999) y else null
}
