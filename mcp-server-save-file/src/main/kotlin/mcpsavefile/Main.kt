package mcpsavefile

import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking { McpServer().run() }
}
