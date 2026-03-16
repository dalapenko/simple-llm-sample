package indexer.document

import java.io.File

/**
 * A document loaded from disk, ready for chunking.
 */
data class LoadedDocument(
    val file: File,
    val content: String
)

/**
 * Walks a directory recursively and loads text content from all supported files.
 * Files that cannot be extracted (unsupported format, empty, or I/O errors) are silently skipped.
 */
class DocumentLoader(
    private val extractors: List<TextExtractor> = listOf(PlainTextExtractor(), PdfExtractor())
) {

    fun loadDirectory(directory: File): List<LoadedDocument> {
        require(directory.isDirectory) { "Not a directory: ${directory.absolutePath}" }

        return directory.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val extractor = extractors.find { it.supports(file) } ?: return@mapNotNull null
                try {
                    val content = extractor.extract(file)
                    if (content.isBlank()) null else LoadedDocument(file, content)
                } catch (e: Exception) {
                    System.err.println("WARN: Skipping ${file.name} — ${e.message}")
                    null
                }
            }
            .toList()
    }
}
