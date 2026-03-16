package indexer.chunker

import java.io.File

/**
 * Splits documents by their natural structure:
 * - **Markdown** (.md): split at each header (#, ##, ###)
 * - **Kotlin** (.kt, .kts): split at each top-level declaration (class, fun, object, interface)
 * - **Other** (PDF text, .txt): split at paragraph boundaries (two or more blank lines)
 *
 * This strategy produces semantically coherent chunks with high metadata density,
 * since nearly every chunk is associated with a named section or declaration.
 */
class StructuralChunker : Chunker {

    override val strategyName = "structural"

    override fun chunk(content: String, sourceFile: File): List<Chunk> {
        if (content.isBlank()) return emptyList()

        return when (sourceFile.extension.lowercase()) {
            "md" -> chunkMarkdown(content, sourceFile)
            "kt", "kts" -> chunkKotlin(content, sourceFile)
            else -> chunkByParagraph(content, sourceFile)
        }
    }

    // ── Markdown ────────────────────────────────────────────────────────────────

    private val headerRegex = Regex("^(#{1,3})\\s+(.+)$", RegexOption.MULTILINE)

    private fun chunkMarkdown(content: String, file: File): List<Chunk> {
        val title = file.nameWithoutExtension
        val matches = headerRegex.findAll(content).toList()

        if (matches.isEmpty()) return chunkByParagraph(content, file)

        val chunks = mutableListOf<Chunk>()

        // Content before the first header (intro / frontmatter)
        val preamble = content.substring(0, matches.first().range.first).trim()
        if (preamble.isNotBlank()) {
            chunks += makeChunk(preamble, file.absolutePath, title, null)
        }

        for ((i, match) in matches.withIndex()) {
            val sectionTitle = match.groupValues[2].trim()
            val bodyStart = match.range.last + 1
            val bodyEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val sectionText = "${match.value}\n${content.substring(bodyStart, bodyEnd)}".trim()

            if (sectionText.isNotBlank()) {
                chunks += makeChunk(sectionText, file.absolutePath, title, sectionTitle)
            }
        }

        return chunks
    }

    // ── Kotlin ──────────────────────────────────────────────────────────────────

    // Matches top-level declarations — only lines starting at column 0 (no leading spaces).
    // Group 1 captures the declaration name (e.g. "Foo", "topLevelFn", "MyObject").
    private val ktDeclRegex = Regex(
        """^(?:(?:public|private|internal|protected|abstract|sealed|data|inline|open|operator|suspend|override|external|expect|actual)\s+)*(?:class|object|interface|fun|typealias|enum\s+class)\s+(\w+)""",
        setOf(RegexOption.MULTILINE)
    )

    private fun chunkKotlin(content: String, file: File): List<Chunk> {
        val title = file.nameWithoutExtension
        val matches = ktDeclRegex.findAll(content).toList()

        if (matches.isEmpty()) return chunkByParagraph(content, file)

        val chunks = mutableListOf<Chunk>()

        // Package declaration + imports preamble
        val preamble = content.substring(0, matches.first().range.first).trim()
        if (preamble.isNotBlank()) {
            chunks += makeChunk(preamble, file.absolutePath, title, "package")
        }

        for ((i, match) in matches.withIndex()) {
            val declEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val declText = content.substring(match.range.first, declEnd).trim()
            val sectionName = match.groupValues[1]   // captured name from group 1

            if (declText.isNotBlank()) {
                chunks += makeChunk(declText, file.absolutePath, title, sectionName)
            }
        }

        return chunks
    }

    // ── Paragraph fallback ──────────────────────────────────────────────────────

    private fun chunkByParagraph(content: String, file: File): List<Chunk> {
        val title = file.nameWithoutExtension
        return content.split(Regex("\\n{2,}"))
            .mapIndexedNotNull { i, para ->
                val trimmed = para.trim()
                if (trimmed.isBlank()) null
                else makeChunk(trimmed, file.absolutePath, title, "paragraph-${i + 1}")
            }
    }

    // ── Shared factory ───────────────────────────────────────────────────────────

    private fun makeChunk(content: String, sourcePath: String, title: String, section: String?): Chunk =
        Chunk(
            metadata = ChunkMetadata(
                sourcePath = sourcePath,
                title = title,
                section = section,
                strategy = strategyName
            ),
            content = content
        )
}
