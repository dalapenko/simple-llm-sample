package indexer.document

import java.io.File

/**
 * Extracts text from plain-text source files: .md, .kt, .txt, .java, .py, .ts, etc.
 */
class PlainTextExtractor : TextExtractor {

    private val supportedExtensions = setOf(
        "txt", "md", "kt", "kts", "java", "py", "js", "ts",
        "sh", "yaml", "yml", "json", "xml", "gradle", "toml", "properties"
    )

    override fun supports(file: File): Boolean =
        file.extension.lowercase() in supportedExtensions

    override fun extract(file: File): String = file.readText(Charsets.UTF_8)
}
