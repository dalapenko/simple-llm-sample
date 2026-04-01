package mcpsupport

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    // Data path: first CLI arg, or env var, or default ~/.llmchat/support-data/users_tickets.json
    val dataPath = args.firstOrNull()
        ?: System.getenv("SUPPORT_DATA_PATH")
        ?: (System.getProperty("user.home") + "/.llmchat/support-data/users_tickets.json")

    runBlocking {
        McpServer(CrmRepository(dataPath)).run()
    }
}
