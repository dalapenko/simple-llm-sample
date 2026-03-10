package llmchat.agent.context

import llmchat.agent.TokenCounter

/**
 * A named, independent conversation branch.
 *
 * @property id       Unique identifier (e.g. "branch2").
 * @property name     Human-readable label chosen by the user.
 * @property messages Full message history for this branch.
 */
data class Branch(
    val id: String,
    val name: String,
    val messages: MutableList<Pair<String, String>> = mutableListOf()
)

/**
 * A named save-point at a specific position inside a branch.
 *
 * @property id           Unique identifier (e.g. "cp3").
 * @property name         Human-readable label chosen by the user.
 * @property branchId     The branch this checkpoint belongs to.
 * @property messageCount Number of messages in the branch at save time.
 */
data class Checkpoint(
    val id: String,
    val name: String,
    val branchId: String,
    val messageCount: Int
)

/**
 * Branching context strategy.
 *
 * Maintains multiple independent conversation branches. A checkpoint can be
 * saved at any point in any branch, and a new branch can be forked from any
 * checkpoint — allowing two (or more) completely separate continuations of
 * the same dialog.
 *
 * Concepts:
 *  - Branch   : independent sequence of exchanges
 *  - Checkpoint: a named save-point in a branch (records message count)
 *  - Fork     : create a new branch pre-seeded with messages up to a checkpoint
 *
 * The context block always reflects the CURRENT branch only. Switching branches
 * transparently changes which history the LLM sees on the next request.
 *
 * @param windowSize Maximum number of recent exchanges shown to the LLM at once.
 */
class BranchingStrategy(private val windowSize: Int = 20) : ContextStrategy {

    private val branches = mutableMapOf<String, Branch>()
    private val checkpoints = mutableListOf<Checkpoint>()
    private var currentBranchId = "main"
    private var branchCounter = 0
    private var checkpointCounter = 0

    init {
        branches["main"] = Branch("main", "main")
    }

    val currentBranch: Branch
        get() = branches[currentBranchId] ?: error("Current branch '$currentBranchId' not found")

    // ── ContextStrategy ────────────────────────────────────────────────────────

    override suspend fun addMessage(user: String, assistant: String) {
        currentBranch.messages.add(user to assistant)
    }

    override fun loadMessages(messages: List<Pair<String, String>>) {
        // Load into the "main" branch on session restore.
        branches["main"]!!.messages.apply {
            clear()
            addAll(messages)
        }
        currentBranchId = "main"
    }

    override fun buildContextBlock(): String {
        val branch = currentBranch
        val displayMessages = branch.messages.takeLast(windowSize)
        if (displayMessages.isEmpty()) return ""

        val history = displayMessages.joinToString("\n\n") { (user, assistant) ->
            "Previous exchange:\nUser: $user\nAssistant: $assistant"
        }
        return "[Branch: ${branch.name}] Context from recent conversation (last ${displayMessages.size} exchanges):\n" +
                "$history\n\nPlease continue the conversation naturally."
    }

    override fun clearHistory() {
        branches.clear()
        checkpoints.clear()
        currentBranchId = "main"
        branchCounter = 0
        checkpointCounter = 0
        branches["main"] = Branch("main", "main")
    }

    override fun estimateTokenStats(): ContextTokenStats {
        val tokens = currentBranch.messages.sumOf { (u, a) ->
            TokenCounter.estimate(u) + TokenCounter.estimate(a)
        }
        return ContextTokenStats(primary = tokens)
    }

    override fun displayHistory() {
        val branch = currentBranch
        println("\n=== Branching History [Branch: ${branch.name}] (${branch.messages.size} exchanges) ===")
        if (branch.messages.isEmpty()) {
            println("  No messages in this branch yet.")
        } else {
            branch.messages.forEachIndexed { i, (user, assistant) ->
                println("\n  [${i + 1}] You: $user")
                println("       Assistant: $assistant")
            }
        }
        println("=========================================================\n")
    }

    // ── Branch management ──────────────────────────────────────────────────────

    /**
     * Save a checkpoint at the current position in the current branch.
     * Returns the created [Checkpoint].
     */
    fun saveCheckpoint(name: String): Checkpoint {
        require(name.isNotBlank()) { "Checkpoint name must not be blank" }
        checkpointCounter++
        val cp = Checkpoint(
            id = "cp$checkpointCounter",
            name = name,
            branchId = currentBranchId,
            messageCount = currentBranch.messages.size
        )
        checkpoints.add(cp)
        return cp
    }

    /**
     * Create a new branch pre-seeded with messages from [fromCheckpointName],
     * or forked from the current state when [fromCheckpointName] is null.
     * Switches the active branch to the new one and returns it.
     */
    fun createBranch(name: String, fromCheckpointName: String? = null): Branch {
        require(name.isNotBlank()) { "Branch name must not be blank" }
        require(branches.values.none { it.name == name }) {
            "A branch named '$name' already exists"
        }

        branchCounter++
        val branchId = "branch$branchCounter"

        val seedMessages: MutableList<Pair<String, String>> = if (fromCheckpointName != null) {
            val cp = checkpoints.find { it.name == fromCheckpointName }
                ?: throw IllegalArgumentException(
                    "Checkpoint '$fromCheckpointName' not found. Use /checkpoint list to see saved checkpoints."
                )
            val source = branches[cp.branchId]
                ?: throw IllegalStateException("Source branch for checkpoint not found")
            source.messages.take(cp.messageCount).toMutableList()
        } else {
            currentBranch.messages.toMutableList()
        }

        val newBranch = Branch(id = branchId, name = name, messages = seedMessages)
        branches[branchId] = newBranch
        currentBranchId = branchId
        return newBranch
    }

    /**
     * Switch the active branch to the one identified by [nameOrId].
     * Returns the branch switched to.
     */
    fun switchBranch(nameOrId: String): Branch {
        val branch = branches.values.find { it.name == nameOrId || it.id == nameOrId }
            ?: throw IllegalArgumentException(
                "Branch '$nameOrId' not found. Use /branch list to see available branches."
            )
        currentBranchId = branch.id
        return branch
    }

    fun listBranches(): List<Branch> = branches.values.toList()

    fun listCheckpoints(): List<Checkpoint> = checkpoints.toList()

    fun currentBranchName(): String = currentBranch.name
}
