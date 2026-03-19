package indexer.search

import indexer.store.IndexEntry

object ContextAssembler {

    fun assemble(entries: List<IndexEntry>): String {
        if (entries.isEmpty()) return ""
        return buildString {
            entries.forEachIndexed { i, entry ->
                if (i > 0) append("\n\n---\n\n")
                val meta = entry.chunk.metadata
                val filename = meta.sourcePath.substringAfterLast("/")
                val section = meta.section?.let { ", Раздел: $it" } ?: ""
                append("Источник: $filename$section:\n")
                append(entry.chunk.content)
            }
        }
    }

    /**
     * Same as [assemble] but prefixes each chunk with its short chunk ID so the LLM can cite it.
     * Format: `[ID: <first-8-chars-of-uuid>] Источник: filename, Раздел: section`
     */
    fun assembleWithIds(entries: List<IndexEntry>): String {
        if (entries.isEmpty()) return ""
        return buildString {
            entries.forEachIndexed { i, entry ->
                if (i > 0) append("\n\n---\n\n")
                val meta = entry.chunk.metadata
                val filename = meta.sourcePath.substringAfterLast("/")
                val section = meta.section?.let { ", Раздел: $it" } ?: ""
                val shortId = meta.chunkId.take(8)
                append("[ID: $shortId] Источник: $filename$section:\n")
                append(entry.chunk.content)
            }
        }
    }

    fun summarizeSources(entries: List<IndexEntry>): List<String> =
        entries.map { entry ->
            val filename = entry.chunk.metadata.sourcePath.substringAfterLast("/")
            val section = entry.chunk.metadata.section?.let { " ($it)" } ?: ""
            "$filename$section"
        }

    fun extractChunkIds(entries: List<IndexEntry>): List<String> =
        entries.map { it.chunk.metadata.chunkId.take(8) }
}
