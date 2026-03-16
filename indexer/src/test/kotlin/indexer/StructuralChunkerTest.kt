package indexer

import indexer.chunker.StructuralChunker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuralChunkerTest {

    private val chunker = StructuralChunker()

    @Test
    fun `empty content returns no chunks`() {
        val chunks = chunker.chunk("", File("/fake/doc.md"))
        assertEquals(0, chunks.size)
    }

    @Test
    fun `markdown with headers splits into per-section chunks`() {
        val content = """
            # Introduction
            This is the intro.

            ## Section One
            Content of section one.

            ## Section Two
            Content of section two.
        """.trimIndent()

        val chunks = chunker.chunk(content, File("/fake/readme.md"))
        // Expect: Introduction, Section One, Section Two
        assertEquals(3, chunks.size)
        assertEquals("Introduction", chunks[0].metadata.section)
        assertEquals("Section One", chunks[1].metadata.section)
        assertEquals("Section Two", chunks[2].metadata.section)
    }

    @Test
    fun `markdown without headers falls back to paragraph splitting`() {
        val content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val chunks = chunker.chunk(content, File("/fake/plain.md"))
        assertEquals(3, chunks.size)
    }

    @Test
    fun `markdown preamble before first header is preserved`() {
        val content = """
            This is preamble text before any heading.

            # First Header
            Body of first section.
        """.trimIndent()

        val chunks = chunker.chunk(content, File("/fake/doc.md"))
        assertEquals(2, chunks.size)
        assertEquals(null, chunks[0].metadata.section)   // preamble has no section
        assertEquals("First Header", chunks[1].metadata.section)
    }

    @Test
    fun `kotlin file splits at top-level declarations`() {
        val content = """
            package com.example

            import java.util.UUID

            class Foo {
                fun bar() = "bar"
            }

            fun topLevelFn(): Int = 42

            object MyObject {
                const val VALUE = 1
            }
        """.trimIndent()

        val chunks = chunker.chunk(content, File("/fake/Foo.kt"))
        // Expect: package preamble, Foo class, topLevelFn, MyObject
        assertTrue(chunks.size >= 3)
        val sections = chunks.map { it.metadata.section }
        assertTrue("package" in sections)
        assertTrue(sections.any { it?.contains("Foo") == true || it?.contains("topLevelFn") == true })
    }

    @Test
    fun `kotlin file with no declarations falls back to paragraphs`() {
        val content = "// just comments\n\n// more comments"
        val chunks = chunker.chunk(content, File("/fake/Empty.kt"))
        assertTrue(chunks.isNotEmpty())
    }

    @Test
    fun `plain text file splits by paragraph`() {
        val content = "Paragraph one.\n\nParagraph two.\n\nParagraph three."
        val chunks = chunker.chunk(content, File("/fake/article.txt"))
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.metadata.section?.startsWith("paragraph-") == true })
    }

    @Test
    fun `strategy name is always structural`() {
        val chunks = chunker.chunk("# Title\nContent.", File("/fake/doc.md"))
        assertTrue(chunks.all { it.metadata.strategy == "structural" })
    }

    @Test
    fun `source path and title are set correctly`() {
        val file = File("/some/path/MyDoc.md")
        val chunks = chunker.chunk("# Header\nBody.", file)
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertEquals("/some/path/MyDoc.md", chunk.metadata.sourcePath)
            assertEquals("MyDoc", chunk.metadata.title)
        }
    }
}
