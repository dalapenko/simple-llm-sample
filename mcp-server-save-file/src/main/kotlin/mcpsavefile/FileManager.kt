package mcpsavefile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val ALLOWED_BASE: Path = Path.of(System.getProperty("user.home"), ".llmchat", "pipeline-results")
private val ALLOWED_FORMATS = setOf("json", "txt", "md")

class FileManager {

    fun save(filename: String, content: String, format: String = "txt"): SaveResult {
        require(filename.isNotBlank()) { "filename must not be blank" }
        require(!filename.contains('/') && !filename.contains('\\')) {
            "filename must not contain path separators"
        }
        require(!filename.contains("..")) { "filename must not contain '..'" }
        require(!filename.startsWith(".")) { "filename must not start with '.'" }

        val effectiveFormat = format.lowercase()
        require(effectiveFormat in ALLOWED_FORMATS) {
            "format must be one of: ${ALLOWED_FORMATS.joinToString()}"
        }

        Files.createDirectories(ALLOWED_BASE)
        val canonicalBase = ALLOWED_BASE.toRealPath()

        val targetFile = ALLOWED_BASE.resolve(filename).normalize()
        val canonicalTarget = targetFile.toAbsolutePath().normalize()
        require(canonicalTarget.startsWith(canonicalBase)) {
            "Resolved path escapes the allowed directory"
        }

        val existed = Files.exists(targetFile)
        Files.writeString(targetFile, content, StandardCharsets.UTF_8)
        val sizeBytes = Files.size(targetFile)

        return SaveResult(
            success = true,
            filePath = targetFile.toAbsolutePath().toString(),
            sizeBytes = sizeBytes,
            format = effectiveFormat,
            overwritten = existed
        )
    }
}

data class SaveResult(
    val success: Boolean,
    val filePath: String,
    val sizeBytes: Long,
    val format: String,
    val overwritten: Boolean
)
