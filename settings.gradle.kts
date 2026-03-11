plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "simple-llm-sample"

include(":cli-app")
include(":mcp-server-jsonplaceholder")
include(":mcp-server-scheduler")
