package mcppipeline

import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        McpServer().run()
    }
}
