# User Profile — Sample

<!--
  This file is injected into the system prompt on every request.
  Use it to describe your preferences once so you never have to repeat them.

  Usage:
    ./gradlew run --args="--profile profiles/sample.md"

  Or copy and customize it:
    cp profiles/sample.md ~/.llmchat/profile.md
-->

## Communication Style

- Preferred language: English
- Response length: concise — short, focused answers; expand only when the complexity genuinely demands it
- Tone: direct and professional; skip pleasantries and filler
- Format: use Markdown when it adds clarity (code blocks, lists, headers); plain prose otherwise
- Do not repeat the question back before answering
- Do not use filler phrases ("Certainly!", "Great question!", "Of course!")
- Do not add disclaimers or caveats unless they are technically material

## Code Preferences

- Primary language: Kotlin (JVM, targets Java 21)
- Style: idiomatic Kotlin — prefer data classes, sealed classes, extension functions, and scope functions
- Naming: follow Kotlin coding conventions (camelCase, descriptive names, no Hungarian notation)
- Comments: only when the intent is not obvious from the code; no redundant inline comments
- Error handling: explicit — prefer `Result<T>` or sealed error types over unchecked exceptions for domain errors
- Null safety: leverage Kotlin's type system fully; avoid `!!` except in provably-safe contexts
- Avoid over-engineering: no abstractions, helpers, or utility classes for one-time-use logic
- Tests: JUnit 5 with `kotlin.test` assertions; use `@TempDir` for file-backed tests

## Frameworks & Libraries in Use

- LLM framework: Koog (`ai.koog:koog-agents`)
- TUI output: Mordant 3.x (`com.github.ajalt.mordant`)
- TUI input: JLine3 (`org.jline:jline`)
- Serialization: `kotlinx.serialization` (JSON)
- Build: Gradle with the Shadow plugin for fat-JAR

## Output Constraints

- Prefer concrete code examples over abstract explanations
- When showing code, include only the relevant snippet — not the entire file
- When listing options, keep it to 3–5 items max; do not exhaustively enumerate every edge case
- If you are unsure about something, say so explicitly rather than guessing

## Context

- I am building a Kotlin JVM CLI tool for interacting with LLMs via OpenRouter
- The project uses a multi-strategy conversation memory system (sliding window, sticky facts, branching, layered)
- File storage lives in `~/.llmchat/`
- Architecture decisions are firm — do not suggest replacing existing dependencies unless asked
