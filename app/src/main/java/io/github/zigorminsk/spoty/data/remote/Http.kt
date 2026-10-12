package io.github.zigorminsk.spoty.data.remote

import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HttpStatusException(val code: Int, val url: String) : IOException("HTTP $code")

/** Общий JSON: терпим к лишним полям и null вместо значений по умолчанию. */
val SpotyJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Тонкая обёртка над OkHttp: suspend-GET с отменой и разбором JSON. */
class Http(val client: OkHttpClient, val json: Json = SpotyJson) {

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String =
        suspendCancellableCoroutine { cont ->
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            headers.forEach { (k, v) -> builder.header(k, v) }
            val call = client.newCall(builder.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        try {
                            if (!r.isSuccessful) {
                                if (cont.isActive) cont.resumeWithException(HttpStatusException(r.code, url))
                                return
                            }
                            val text = r.body?.string().orEmpty()
                            if (cont.isActive) cont.resume(text)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            })
        }

    suspend fun getElement(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        json.parseToJsonElement(getString(url, headers))

    suspend inline fun <reified T> getJson(url: String, headers: Map<String, String> = emptyMap()): T =
        json.decodeFromString(getString(url, headers))
}

fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

/** Выполняет [block]; при любой ошибке (кроме отмены) возвращает [default]. */
suspend inline fun <T> safely(default: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    default
}

// ───────────── Помощники для «неаккуратного» JSON ─────────────

/** Строка из примитива или первого элемента массива. */
fun JsonElement?.firstString(): String? = when (this) {
    is JsonPrimitive -> if (isString || content != "null") content.takeIf { it.isNotBlank() } else null
    is JsonArray -> firstNotNullOfOrNull { it.firstString() }
    else -> null
}

fun JsonElement?.asLong(): Long? = when (this) {
    is JsonPrimitive -> longOrNull ?: doubleOrNull?.toLong() ?: content.trim().toLongOrNull()
    else -> null
}

fun JsonElement?.asDouble(): Double? = when (this) {
    is JsonPrimitive -> doubleOrNull ?: content.trim().toDoubleOrNull()
    else -> null
}

/** Разбирает массив (или одиночный объект) в список, пропуская «битые» элементы. */
inline fun <reified T> JsonElement?.decodeList(json: Json): List<T> = when (this) {
    is JsonArray -> mapNotNull { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }
    is JsonObject -> listOfNotNull(runCatching { json.decodeFromJsonElement<T>(this) }.getOrNull())
    else -> emptyList()
}
