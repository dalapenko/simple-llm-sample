package mcpsearch

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DuckDuckGoClient {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }

    suspend fun search(query: String, limit: Int = 5): JsonElement {
        val responseText: String = http.get("https://api.duckduckgo.com/") {
            parameter("q", query)
            parameter("format", "json")
            parameter("no_html", "1")
            parameter("skip_disambig", "1")
            header("User-Agent", "mcp-server-search/1.0")
        }.bodyAsText()

        val parsed = Json.parseToJsonElement(responseText).jsonObject
        val items = mutableListOf<JsonObject>()

        val answer = parsed["Answer"]?.jsonPrimitive?.contentOrNull
        if (!answer.isNullOrBlank()) {
            items.add(buildJsonObject {
                put("title", "Direct Answer")
                put("excerpt", answer)
                put("url", "")
                put("relevanceScore", 1.0)
            })
        }

        val abstractText = parsed["AbstractText"]?.jsonPrimitive?.contentOrNull
        val abstractUrl = parsed["AbstractURL"]?.jsonPrimitive?.contentOrNull
        if (!abstractText.isNullOrBlank()) {
            items.add(buildJsonObject {
                put("title", parsed["Heading"]?.jsonPrimitive?.contentOrNull ?: query)
                put("excerpt", abstractText)
                put("url", abstractUrl ?: "")
                put("relevanceScore", 1.0)
            })
        }

        val topics = parsed["RelatedTopics"]?.jsonArray ?: buildJsonArray {}
        for (topic in topics) {
            if (items.size >= limit) break
            val obj = topic as? JsonObject ?: continue
            val text = obj["Text"]?.jsonPrimitive?.contentOrNull
            if (text != null) {
                val url = obj["FirstURL"]?.jsonPrimitive?.contentOrNull ?: ""
                items.add(buildJsonObject {
                    put("title", text.take(80))
                    put("excerpt", text)
                    put("url", url)
                    put("relevanceScore", (0.8 - items.size * 0.05).coerceAtLeast(0.1))
                })
            } else {
                val subTopics = obj["Topics"]?.jsonArray ?: continue
                for (subTopic in subTopics) {
                    if (items.size >= limit) break
                    val subObj = subTopic as? JsonObject ?: continue
                    val subText = subObj["Text"]?.jsonPrimitive?.contentOrNull ?: continue
                    val subUrl = subObj["FirstURL"]?.jsonPrimitive?.contentOrNull ?: ""
                    items.add(buildJsonObject {
                        put("title", subText.take(80))
                        put("excerpt", subText)
                        put("url", subUrl)
                        put("relevanceScore", (0.7 - items.size * 0.05).coerceAtLeast(0.1))
                    })
                }
            }
        }

        return buildJsonObject {
            put("query", query)
            put("items", buildJsonArray { items.take(limit).forEach { add(it) } })
            put("totalItems", items.size.coerceAtMost(limit))
        }
    }

    fun close() = http.close()
}
