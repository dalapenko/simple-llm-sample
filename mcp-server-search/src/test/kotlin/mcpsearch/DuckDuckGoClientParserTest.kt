package mcpsearch

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDuckGoClientParserTest {

    private val client = DuckDuckGoClient()

    // ── empty / minimal responses ─────────────────────────────────────────────

    @Test
    fun `empty response returns empty items list`() {
        val result = client.parseResponse(buildJsonObject {}, "test query", 5)
        assertEquals("test query", result.jsonObject["query"]!!.jsonPrimitive.content)
        assertEquals(0, result.jsonObject["totalItems"]!!.jsonPrimitive.int)
        assertEquals(0, result.jsonObject["items"]!!.jsonArray.size)
    }

    @Test
    fun `query field is preserved in result`() {
        val result = client.parseResponse(buildJsonObject {}, "my specific query", 5)
        assertEquals("my specific query", result.jsonObject["query"]!!.jsonPrimitive.content)
    }

    // ── Direct Answer ─────────────────────────────────────────────────────────

    @Test
    fun `direct answer creates item with title Direct Answer and relevance 1_0`() {
        val raw = buildJsonObject { put("Answer", "42 is the answer to everything") }
        val items = client.parseResponse(raw, "meaning", 5).jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size)
        val item = items[0].jsonObject
        assertEquals("Direct Answer", item["title"]!!.jsonPrimitive.content)
        assertEquals("42 is the answer to everything", item["excerpt"]!!.jsonPrimitive.content)
        assertEquals("", item["url"]!!.jsonPrimitive.content)
        assertEquals(1.0, item["relevanceScore"]!!.jsonPrimitive.double)
    }

    @Test
    fun `blank answer is ignored`() {
        val raw = buildJsonObject {
            put("Answer", "   ")
            put("AbstractText", "Some abstract text")
            put("Heading", "Heading")
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size)
        assertEquals("Heading", items[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    // ── Abstract ──────────────────────────────────────────────────────────────

    @Test
    fun `abstract text uses heading as title`() {
        val raw = buildJsonObject {
            put("AbstractText", "Kotlin is a modern programming language")
            put("AbstractURL", "https://kotlinlang.org")
            put("Heading", "Kotlin")
        }
        val items = client.parseResponse(raw, "kotlin", 5).jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size)
        val item = items[0].jsonObject
        assertEquals("Kotlin", item["title"]!!.jsonPrimitive.content)
        assertEquals("Kotlin is a modern programming language", item["excerpt"]!!.jsonPrimitive.content)
        assertEquals("https://kotlinlang.org", item["url"]!!.jsonPrimitive.content)
        assertEquals(1.0, item["relevanceScore"]!!.jsonPrimitive.double)
    }

    @Test
    fun `abstract text falls back to query when heading is missing`() {
        val raw = buildJsonObject { put("AbstractText", "Some description") }
        val items = client.parseResponse(raw, "fallback query", 5).jsonObject["items"]!!.jsonArray
        assertEquals("fallback query", items[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `blank abstract text is ignored`() {
        val raw = buildJsonObject { put("AbstractText", "") }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals(0, items.size)
    }

    @Test
    fun `abstract url defaults to empty string when missing`() {
        val raw = buildJsonObject {
            put("AbstractText", "Description without URL")
            put("Heading", "Title")
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals("", items[0].jsonObject["url"]!!.jsonPrimitive.content)
    }

    // ── Related Topics ────────────────────────────────────────────────────────

    @Test
    fun `related topics are extracted with url and excerpt`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject {
                    put("Text", "Topic one description")
                    put("FirstURL", "https://example.com/1")
                })
                add(buildJsonObject {
                    put("Text", "Topic two description")
                    put("FirstURL", "https://example.com/2")
                })
            })
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("Topic one description", items[0].jsonObject["excerpt"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/1", items[0].jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("Topic two description", items[1].jsonObject["excerpt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `topic url defaults to empty string when FirstURL is missing`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject { put("Text", "Topic without URL") })
            })
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals("", items[0].jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `topic title is truncated to 80 characters`() {
        val longText = "A".repeat(120)
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject { put("Text", longText); put("FirstURL", "") })
            })
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals(80, items[0].jsonObject["title"]!!.jsonPrimitive.content.length)
        assertEquals(longText, items[0].jsonObject["excerpt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `limit is respected when processing related topics`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                repeat(10) { i ->
                    add(buildJsonObject { put("Text", "Topic $i"); put("FirstURL", "https://example.com/$i") })
                }
            })
        }
        val result = client.parseResponse(raw, "test", 3)
        assertEquals(3, result.jsonObject["items"]!!.jsonArray.size)
        assertEquals(3, result.jsonObject["totalItems"]!!.jsonPrimitive.int)
    }

    // ── Nested Sub-topics ─────────────────────────────────────────────────────

    @Test
    fun `nested subtopics are extracted`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject {
                    put("Topics", buildJsonArray {
                        add(buildJsonObject { put("Text", "Sub Topic A"); put("FirstURL", "https://sub.example.com/a") })
                        add(buildJsonObject { put("Text", "Sub Topic B"); put("FirstURL", "https://sub.example.com/b") })
                    })
                })
            })
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("Sub Topic A", items[0].jsonObject["excerpt"]!!.jsonPrimitive.content)
        assertEquals("https://sub.example.com/a", items[0].jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `limit is respected within nested subtopics`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject {
                    put("Topics", buildJsonArray {
                        repeat(10) { i ->
                            add(buildJsonObject { put("Text", "Sub $i"); put("FirstURL", "https://sub.example.com/$i") })
                        }
                    })
                })
            })
        }
        val result = client.parseResponse(raw, "test", 4)
        assertEquals(4, result.jsonObject["items"]!!.jsonArray.size)
    }

    // ── Relevance scoring ─────────────────────────────────────────────────────

    @Test
    fun `topic relevance score decays with position`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                repeat(5) { i ->
                    add(buildJsonObject { put("Text", "Topic $i"); put("FirstURL", "https://example.com/$i") })
                }
            })
        }
        val items = client.parseResponse(raw, "test", 5).jsonObject["items"]!!.jsonArray
        val score0 = items[0].jsonObject["relevanceScore"]!!.jsonPrimitive.double
        val score1 = items[1].jsonObject["relevanceScore"]!!.jsonPrimitive.double
        val score2 = items[2].jsonObject["relevanceScore"]!!.jsonPrimitive.double
        assertTrue(score0 > score1, "Score should decrease: $score0 > $score1")
        assertTrue(score1 > score2, "Score should decrease: $score1 > $score2")
    }

    @Test
    fun `relevance score never drops below 0_1`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                repeat(20) { i ->
                    add(buildJsonObject { put("Text", "Topic $i"); put("FirstURL", "") })
                }
            })
        }
        val items = client.parseResponse(raw, "test", 20).jsonObject["items"]!!.jsonArray
        items.forEach { item ->
            val score = item.jsonObject["relevanceScore"]!!.jsonPrimitive.double
            assertTrue(score >= 0.1, "Score $score must be >= 0.1")
        }
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Test
    fun `items appear in order - direct answer then abstract then related topics`() {
        val raw = buildJsonObject {
            put("Answer", "Direct answer text")
            put("AbstractText", "Abstract description")
            put("AbstractURL", "https://abstract.example.com")
            put("Heading", "Abstract Title")
            put("RelatedTopics", buildJsonArray {
                add(buildJsonObject { put("Text", "Related topic"); put("FirstURL", "https://related.example.com") })
            })
        }
        val items = client.parseResponse(raw, "test", 10).jsonObject["items"]!!.jsonArray
        assertEquals(3, items.size)
        assertEquals("Direct Answer", items[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("Abstract Title", items[1].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("Related topic", items[2].jsonObject["excerpt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `limit applies to total items including answer and abstract`() {
        val raw = buildJsonObject {
            put("Answer", "Direct answer")
            put("AbstractText", "Abstract text")
            put("Heading", "Heading")
            put("RelatedTopics", buildJsonArray {
                repeat(10) { i ->
                    add(buildJsonObject { put("Text", "Topic $i"); put("FirstURL", "") })
                }
            })
        }
        // limit=2 should stop after Answer + Abstract (both have relevance 1.0)
        val result = client.parseResponse(raw, "test", 2)
        assertEquals(2, result.jsonObject["items"]!!.jsonArray.size)
        assertEquals(2, result.jsonObject["totalItems"]!!.jsonPrimitive.int)
    }

    // ── totalItems consistency ────────────────────────────────────────────────

    @Test
    fun `totalItems matches items array size`() {
        val raw = buildJsonObject {
            put("RelatedTopics", buildJsonArray {
                repeat(3) { i ->
                    add(buildJsonObject { put("Text", "Topic $i"); put("FirstURL", "") })
                }
            })
        }
        val result = client.parseResponse(raw, "test", 5)
        val items = result.jsonObject["items"]!!.jsonArray
        assertEquals(items.size, result.jsonObject["totalItems"]!!.jsonPrimitive.int)
    }
}
