package indexer.store

import indexer.chunker.Chunk
import indexer.chunker.ChunkMetadata
import indexer.embedding.cosineSimilarity
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.ByteBuffer
import java.sql.Connection
import java.util.*

/**
 * SQLite-backed [VectorStore] using Exposed DSL for schema management and CRUD operations.
 *
 * Schema:
 *   `chunks`     — chunk content + metadata (primary key: UUID string)
 *   `embeddings` — float vector stored as Base64 text, referenced by chunk id
 *
 * Embeddings are Base64-encoded IEEE 754 float bytes — portable and inspectable
 * with any SQL browser.
 */
class SqliteVectorStore(dbPath: String) : VectorStore {

    private val db: Database

    init {
        db = Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            SchemaUtils.create(Chunks, Embeddings)
        }
    }

    // ── Schema ───────────────────────────────────────────────────────────────────

    private object Chunks : Table("chunks") {
        val id = varchar("id", 36)
        val sourcePath = text("source_path")
        val title = varchar("title", 255)
        val section = varchar("section", 255).nullable()
        val strategy = varchar("strategy", 64)
        val content = text("content")
        val tokenEstimate = integer("token_estimate")
        override val primaryKey = PrimaryKey(id)
    }

    private object Embeddings : Table("embeddings") {
        val chunkId = varchar("chunk_id", 36)
        val vector = text("vector")         // Base64-encoded FloatArray
        val dimensions = integer("dimensions")
        override val primaryKey = PrimaryKey(chunkId)
    }

    // ── VectorStore ──────────────────────────────────────────────────────────────

    override fun upsert(entry: IndexEntry) {
        val chunk = entry.chunk
        val id = chunk.metadata.chunkId
        transaction(db) {
            Chunks.deleteWhere { Chunks.id eq id }
            Embeddings.deleteWhere { Embeddings.chunkId eq id }

            Chunks.insert {
                it[Chunks.id] = id
                it[sourcePath] = chunk.metadata.sourcePath
                it[title] = chunk.metadata.title
                it[section] = chunk.metadata.section
                it[strategy] = chunk.metadata.strategy
                it[content] = chunk.content
                it[tokenEstimate] = chunk.tokenEstimate
            }
            Embeddings.insert {
                it[chunkId] = id
                it[vector] = entry.embedding.toBase64()
                it[dimensions] = entry.embedding.size
            }
        }
    }

    override fun search(query: FloatArray, topK: Int): List<IndexEntry> =
        loadAll()
            .sortedByDescending { cosineSimilarity(query, it.embedding) }
            .take(topK)

    override fun stats(): StoreStats = transaction(db) {
        val rows = Chunks.selectAll().toList()
        val total = rows.size
        if (total == 0) return@transaction StoreStats(0, 0.0, 0.0)
        val avgSize = rows.sumOf { it[Chunks.content].length }.toDouble() / total
        val withSection = rows.count { it[Chunks.section] != null }.toDouble()
        StoreStats(total, avgSize, withSection / total)
    }

    override fun clear() {
        transaction(db) {
            Embeddings.deleteAll()
            Chunks.deleteAll()
        }
    }

    override fun close() {
        // Connection lifecycle managed by the sqlite-jdbc driver pool
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private fun loadAll(): List<IndexEntry> = transaction(db) {
        val embMap = Embeddings.selectAll()
            .associate { it[Embeddings.chunkId] to it[Embeddings.vector].fromBase64() }

        Chunks.selectAll().mapNotNull { row ->
            val id = row[Chunks.id]
            val embedding = embMap[id] ?: return@mapNotNull null
            IndexEntry(
                chunk = Chunk(
                    metadata = ChunkMetadata(
                        chunkId = id,
                        sourcePath = row[Chunks.sourcePath],
                        title = row[Chunks.title],
                        section = row[Chunks.section],
                        strategy = row[Chunks.strategy]
                    ),
                    content = row[Chunks.content]
                ),
                embedding = embedding
            )
        }
    }

    private fun FloatArray.toBase64(): String {
        val buf = ByteBuffer.allocate(size * 4)
        forEach { buf.putFloat(it) }
        return Base64.getEncoder().encodeToString(buf.array())
    }

    private fun String.fromBase64(): FloatArray {
        val bytes = Base64.getDecoder().decode(this)
        val buf = ByteBuffer.wrap(bytes)
        return FloatArray(buf.remaining() / 4) { buf.getFloat() }
    }
}
