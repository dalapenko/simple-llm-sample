package llmchat.agent

data class ContextWindowConfig(
    val windowSize: Int = 10,
    val summaryBatchSize: Int = 10
) {
    init {
        require(windowSize >= 1) { "windowSize must be >= 1" }
        require(summaryBatchSize >= 1) { "summaryBatchSize must be >= 1" }
    }
}
