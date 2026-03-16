package indexer.pipeline

/**
 * Per-strategy statistics for a single indexing run.
 */
data class StrategyReport(
    val strategyName: String,
    val totalChunks: Int,
    val avgChunkSize: Double,
    val metadataDensity: Double
)

/**
 * Full report produced after an indexing run, including an optional side-by-side
 * strategy comparison when [--report] is requested.
 */
data class IndexReport(
    val directory: String,
    val filesProcessed: Int,
    val primaryStrategy: StrategyReport,
    val comparisonStrategy: StrategyReport? = null,
    val processingTimeMs: Long
)
