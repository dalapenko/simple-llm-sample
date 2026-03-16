package indexer.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Extracts text from PDF files using Apache PDFBox 3.x.
 *
 * Note: PDFBox 3.x changed the entry-point from `PDDocument.load()` to `Loader.loadPDF()`.
 * Scanned (image-only) PDFs will return empty strings — OCR is out of scope here.
 */
class PdfExtractor : TextExtractor {

    override fun supports(file: File): Boolean =
        file.extension.lowercase() == "pdf"

    override fun extract(file: File): String {
        return Loader.loadPDF(file).use { doc ->
            PDFTextStripper()
                .apply { sortByPosition = true }
                .getText(doc)
        }
    }
}
