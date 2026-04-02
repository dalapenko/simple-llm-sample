package llmchat.agent.filesystem

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

@LLMDescription("File system tools for reading, writing, searching and listing files within the project root, plus running the Kotlin build check.")
class FileSystemToolSet(private val projectRoot: File) : ToolSet {

    private val canonicalRoot: String = projectRoot.canonicalPath

    private fun validateAndResolve(path: String): File {
        val target = File(path).let { if (it.isAbsolute) it else File(projectRoot, path) }
        val canonical = target.canonicalFile
        if (!canonical.path.startsWith(canonicalRoot)) {
            throw IllegalArgumentException("Path escapes project root: $canonical")
        }
        return canonical
    }

    @Tool
    @LLMDescription("Read the full text content of a file inside the project. Returns an error string if the file does not exist, is a directory, or exceeds 100 KB.")
    fun readFile(
        @LLMDescription("Path to the file, relative to the project root or absolute within it.") path: String
    ): String {
        return try {
            val file = validateAndResolve(path)
            if (!file.exists()) return "ERROR: file not found: $path"
            if (file.isDirectory) return "ERROR: path is a directory: $path"
            val size = file.length()
            if (size > 100 * 1024) return "ERROR: file too large (${size / 1024} KB > 100 KB limit): $path"
            file.readText()
        } catch (e: IllegalArgumentException) {
            "ERROR: ${e.message}"
        } catch (e: Exception) {
            "ERROR: failed to read file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Write (overwrite) content to a file inside the project using an atomic temp-file rename. Creates parent directories if needed. Returns 'OK: wrote N bytes to <path>' on success.")
    fun writeFile(
        @LLMDescription("Path to the file, relative to the project root or absolute within it.") path: String,
        @LLMDescription("Full text content to write to the file.") content: String
    ): String {
        return try {
            val file = validateAndResolve(path)
            file.parentFile?.mkdirs()
            val tmpFile = File(file.parent, ".tmp_${file.name}_${System.nanoTime()}")
            tmpFile.writeText(content)
            Files.move(
                tmpFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            val relPath = file.toRelativeString(projectRoot)
            "OK: wrote ${content.toByteArray().size} bytes to $relPath"
        } catch (e: IllegalArgumentException) {
            "ERROR: ${e.message}"
        } catch (e: Exception) {
            "ERROR: failed to write file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Search for a regex pattern in project files matching a glob. Returns results in 'relpath:lineNo: line' format, capped at 200 matches.")
    fun searchFiles(
        @LLMDescription("Java regex pattern to search for within file contents.") pattern: String,
        @LLMDescription("Glob pattern to filter files, e.g. '**/*.kt'. Defaults to '**/*.kt'.") glob: String = "**/*.kt"
    ): String {
        return try {
            val regex = Regex(pattern)
            val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
            val rootPath: Path = projectRoot.toPath()

            val results = mutableListOf<String>()
            Files.walk(rootPath).use { stream ->
                stream.filter { p -> Files.isRegularFile(p) && matcher.matches(rootPath.relativize(p)) }
                    .forEach { filePath ->
                        if (results.size >= 200) return@forEach
                        val relPath = rootPath.relativize(filePath).toString()
                        filePath.toFile().readLines().forEachIndexed { lineIdx, line ->
                            if (results.size < 200 && regex.containsMatchIn(line)) {
                                results.add("$relPath:${lineIdx + 1}: $line")
                            }
                        }
                    }
            }

            if (results.isEmpty()) "No matches found for pattern '$pattern' in glob '$glob'"
            else results.joinToString("\n")
        } catch (e: IllegalArgumentException) {
            "ERROR: ${e.message}"
        } catch (e: Exception) {
            "ERROR: failed to search files: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("List files in a directory inside the project. Returns newline-separated relative paths.")
    fun listFiles(
        @LLMDescription("Directory path, relative to the project root or absolute within it.") directory: String,
        @LLMDescription("Whether to list recursively. Defaults to false.") recursive: Boolean = false
    ): String {
        return try {
            val dir = validateAndResolve(directory)
            if (!dir.exists()) return "ERROR: directory not found: $directory"
            if (!dir.isDirectory) return "ERROR: path is not a directory: $directory"

            val rootPath = projectRoot.toPath()
            val entries = if (recursive) {
                Files.walk(dir.toPath()).filter { Files.isRegularFile(it) }
                    .map { rootPath.relativize(it).toString() }
                    .toList()
            } else {
                dir.listFiles()?.map { rootPath.relativize(it.toPath()).toString() }?.sorted() ?: emptyList()
            }

            if (entries.isEmpty()) "(empty directory)"
            else entries.joinToString("\n")
        } catch (e: IllegalArgumentException) {
            "ERROR: ${e.message}"
        } catch (e: Exception) {
            "ERROR: failed to list files: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Run './gradlew :cli-app:compileKotlin' in the project root and return the result. Returns 'BUILD SUCCESS (Xs)' or 'BUILD FAILED:\\n<last 20 lines of output>'.")
    fun runBuildCheck(): String {
        return try {
            val start = System.currentTimeMillis()
            val process = ProcessBuilder("./gradlew", ":cli-app:compileKotlin")
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(60, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return "BUILD FAILED: timed out after 60 seconds"
            }

            val elapsed = (System.currentTimeMillis() - start) / 1000
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                "BUILD SUCCESS (${elapsed}s)"
            } else {
                val lastLines = output.lines().takeLast(20).joinToString("\n")
                "BUILD FAILED:\n$lastLines"
            }
        } catch (e: Exception) {
            "ERROR: failed to run build: ${e.message}"
        }
    }
}
