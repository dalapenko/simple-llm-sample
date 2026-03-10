# LLM Chat CLI

An interactive command-line interface for chatting with LLMs using the Koog framework and OpenRouter.

Created for AI Advent puzzle solution

![Demonstration](solution/w1d1/demo.gif)

## Features

- 🗨️ **Interactive REPL**: Chat with the LLM in a conversational loop
- 💾 **Conversation History**: Maintains context across multiple messages
- 🎨 **Visual Feedback**: "Thinking..." indicator while waiting for responses
- ⚙️ **Customizable**: Configure system prompts via command-line arguments
- 🛡️ **Error Handling**: Graceful error messages and recovery

## Prerequisites

- JDK 21 or higher
- OpenRouter API key

## Setup

1. Get your OpenRouter API key from [openrouter.ai](https://openrouter.ai)

2. Set the environment variable:
   ```bash
   export OPENROUTER_API_KEY='your-api-key-here'
   ```

## Usage

### Quick Start (Recommended)

The easiest way to start the application:
```bash
./start.sh
```

This script will:
- Check for the API key
- Build the application
- Run the interactive CLI

### Basic Usage

Run the interactive CLI via Gradle:
```bash
./gradlew :cli-app:run --console=plain
```

> **Note**: The `--console=plain` flag is recommended for better interactive experience.

### Alternative: Run the JAR directly

For the best interactive experience, you can build and run the JAR directly:
```bash
./gradlew :cli-app:shadowJar
java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar
```

### With Custom System Prompt

```bash
./gradlew :cli-app:run --console=plain --args="--system-prompt 'You are a coding assistant specialized in Kotlin'"
```

### Show Help

```bash
./gradlew :cli-app:run --args="--help"
```

## Interactive Commands

Once the CLI is running, you can use these commands:

| Command            | Description                  |
|--------------------|------------------------------|
| `/help`            | Show available commands      |
| `/clear`           | Clear conversation history   |
| `/history`         | Display conversation history |
| `/exit` or `/quit` | Exit the application         |

## Example Session

```
╔═══════════════════════════════════════╗
║         LLM Chat CLI                  ║
║  Powered by Koog & OpenRouter         ║
╚═══════════════════════════════════════╝

Type your message and press Enter.
Commands: /help, /clear, /history, /exit

You: Hello! What can you do?
Assistant: I'm a helpful AI assistant. I can answer questions, help with coding, 
explain concepts, and have conversations. How can I help you today?

You: What did I just ask you?
Assistant: You asked me "Hello! What can you do?" - you were inquiring about my 
capabilities.

You: /history

=== Conversation History ===

[1] You: Hello! What can you do?
    Assistant: I'm a helpful AI assistant...

[2] You: What did I just ask you?
    Assistant: You asked me "Hello! What can you do?"...
===========================

You: /exit
Goodbye!
```

## Project Structure

This is a multi-module Gradle project:

```
simple-llm-sample/
├── cli-app/                                 # Interactive LLM chat CLI
│   ├── build.gradle.kts
│   └── src/main/kotlin/llmchat/
│       ├── Main.kt                          # Entry point with REPL loop
│       ├── agent/                           # Conversation, memory, task, MCP client
│       ├── cli/                             # Argument parsing and commands
│       ├── model/                           # Supported LLM models
│       └── ui/                              # Terminal UI components
├── mcp-server-jsonplaceholder/              # Standalone MCP server (JSONPlaceholder API)
│   ├── build.gradle.kts
│   └── src/main/kotlin/mcpserver/
│       ├── Main.kt                          # Entry point (stdio loop)
│       ├── McpServer.kt                     # JSON-RPC 2.0 lifecycle + tool dispatch
│       ├── JsonPlaceholderClient.kt         # Ktor HTTP client
│       └── Models.kt                        # Post, Comment data models
├── build.gradle.kts                         # Root build (plugin version catalog)
└── settings.gradle.kts                      # Module declarations
```

### MCP Server

The `mcp-server-jsonplaceholder` module is a standalone executable that implements the
[Model Context Protocol](https://modelcontextprotocol.io) over stdio. It exposes three tools:

| Tool | Description |
|------|-------------|
| `get_posts` | Fetches posts, optionally filtered by `userId` |
| `get_post_details` | Fetches a single post by `postId` |
| `get_comments` | Fetches comments for a post by `postId` |

Build and connect it from the CLI:
```bash
./gradlew :mcp-server-jsonplaceholder:shadowJar
# Inside the CLI:
/mcp connect java -jar mcp-server-jsonplaceholder/build/libs/mcp-server-jsonplaceholder-1.0-SNAPSHOT-all.jar
```

## Command-Line Options

| Option                   | Description                | Default                          |
|--------------------------|----------------------------|----------------------------------|
| `--help`, `-h`           | Show help message and exit | -                                |
| `--system-prompt "TEXT"` | Custom system prompt       | "You are a helpful assistant..." |

## Environment Variables

| Variable             | Required | Description             |
|----------------------|----------|-------------------------|
| `OPENROUTER_API_KEY` | Yes      | Your OpenRouter API key |

## Troubleshooting

### Application exits immediately when using `./gradlew run`

If the application exits immediately without showing the welcome message, try one of these solutions:

**Solution 1**: Use the `--console=plain` flag:
```bash
./gradlew run --console=plain
```

**Solution 2**: Run the JAR directly (recommended for best experience):
```bash
./gradlew :cli-app:shadowJar
java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar
```

**Solution 3**: Use the distribution scripts:
```bash
./gradlew :cli-app:installDist
./cli-app/build/install/cli-app/bin/cli-app
```

### "OPENROUTER_API_KEY environment variable is not set"

Make sure you've exported the API key:
```bash
export OPENROUTER_API_KEY='your-api-key-here'
```

### "Error communicating with LLM"

This usually indicates a network issue or invalid API key. Check:
- Your internet connection
- That your API key is valid
- That you have credits in your OpenRouter account

## Technology Stack

- **Kotlin**: 2.3.0
- **Koog Framework**: 0.6.2 (AI agent framework)
- **OpenRouter**: LLM API gateway
- **Model**: GPT-4o Mini (via OpenRouter)

## License

This project uses the Koog framework which is licensed under Apache 2.0.
