# Project Integrity Report

Generated: 2026-04-03  
Directory scanned: `cli-app/src`  
Kotlin sources found: 24 files

---

## Source Files

```
cli-app/src/main/kotlin/llmchat/Main.kt
cli-app/src/main/kotlin/llmchat/agent/ChatError.kt
cli-app/src/main/kotlin/llmchat/agent/ConversationManager.kt
cli-app/src/main/kotlin/llmchat/agent/ConversationStorage.kt
cli-app/src/main/kotlin/llmchat/agent/ContextWindowConfig.kt
cli-app/src/main/kotlin/llmchat/agent/RequestStatistics.kt
cli-app/src/main/kotlin/llmchat/agent/TokenCounter.kt
cli-app/src/main/kotlin/llmchat/agent/filesystem/FileSystemToolSet.kt
cli-app/src/main/kotlin/llmchat/agent/mcp/McpConnectionManager.kt
cli-app/src/main/kotlin/llmchat/agent/strategy/ChatGraphStrategy.kt
cli-app/src/main/kotlin/llmchat/cli/CliConfig.kt
cli-app/src/main/kotlin/llmchat/cli/CliParser.kt
cli-app/src/main/kotlin/llmchat/cli/Commands.kt
cli-app/src/main/kotlin/llmchat/cli/LlmProvider.kt
cli-app/src/main/kotlin/llmchat/cli/RagMode.kt
cli-app/src/main/kotlin/llmchat/cli/StrategyType.kt
cli-app/src/main/kotlin/llmchat/model/LLMModels.kt
cli-app/src/main/kotlin/llmchat/rag/AdvancedRagService.kt
cli-app/src/main/kotlin/llmchat/rag/ConversationalRagService.kt
cli-app/src/main/kotlin/llmchat/rag/RagService.kt
cli-app/src/main/kotlin/llmchat/rag/Reranker.kt
cli-app/src/main/kotlin/llmchat/support/SupportRunner.kt
cli-app/src/main/kotlin/llmchat/ui/CliOutput.kt
cli-app/src/main/kotlin/llmchat/ui/ThinkingSpinner.kt
```

---

## Build Check

**Command:** `./gradlew :cli-app:compileKotlin`  
**Result:** BUILD SUCCESS (6s)  
**Exit code:** 0  

Все 24 файла скомпилированы без ошибок и предупреждений.

---

## Зависимости Gradle

| Артефакт                           | Версия       | Статус     |
|------------------------------------|--------------|------------|
| `ai.koog:koog-agents`              | 0.6.2        | ✓ resolved |
| `com.github.ajalt.mordant:mordant` | 3.0.2        | ✓ resolved |
| `org.jline:jline`                  | 3.28.0       | ✓ resolved |
| `com.gradleup.shadow`              | 9.0.0-beta12 | ✓ resolved |

---

## Итог

| Проверка             | Результат                     |
|----------------------|-------------------------------|
| Компиляция Kotlin    | ✓ PASS                        |
| Исходные файлы       | ✓ 24 файла                    |
| Структура пакетов    | ✓ соответствует `llmchat.*`   |
| Модуль `filesystem/` | ✓ интегрирован в ToolRegistry |
| Зависимости          | ✓ все resolved                |
