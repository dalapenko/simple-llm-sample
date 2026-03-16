package indexer.document

import java.io.File

/**
 * Extracts raw text content from a file of a specific format.
 */
interface TextExtractor {
    fun supports(file: File): Boolean
    fun extract(file: File): String
}
