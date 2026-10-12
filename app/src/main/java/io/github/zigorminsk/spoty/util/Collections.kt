package io.github.zigorminsk.spoty.util

/** Чередует элементы списков: по одному из каждого, затем снова (чтобы источники не «слипались»). */
fun <T> interleave(lists: List<List<T>>): List<T> {
    val out = ArrayList<T>()
    val max = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until max) {
        for (list in lists) if (i < list.size) out += list[i]
    }
    return out
}

/** Стабильно поднимает вверх точные и «начинающиеся с» совпадения с запросом. */
fun <T> List<T>.rankByQuery(query: String, text: (T) -> String): List<T> {
    val q = normalizeKey(query)
    if (q.isEmpty()) return this
    return sortedByDescending { item ->
        val t = normalizeKey(text(item))
        when {
            t == q -> 3
            t.startsWith(q) -> 2
            t.contains(q) -> 1
            else -> 0
        }
    }
}

/** Расставляет элементы так, чтобы подряд не шли элементы с одинаковым ключом (по возможности). */
fun <T, K> List<T>.spreadByKey(key: (T) -> K): List<T> {
    if (size < 3) return this
    val rest = ArrayDeque(this)
    val out = ArrayList<T>(size)
    while (rest.isNotEmpty()) {
        val lastKey = out.lastOrNull()?.let(key)
        val idx = rest.indexOfFirst { key(it) != lastKey }.let { if (it < 0) 0 else it }
        out += rest.removeAt(idx)
    }
    return out
}
