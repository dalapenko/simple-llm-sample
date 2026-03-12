plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "simple-llm-sample"

include(":cli-app")
include(":mcp-server-jsonplaceholder")
include(":mcp-server-scheduler")
include(":mcp-server-search")
include(":mcp-server-summarize")
include(":mcp-server-save-file")
