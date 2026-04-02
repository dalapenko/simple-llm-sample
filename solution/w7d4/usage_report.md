# Usage Report: ConversationManager

Generated: 2026-04-03  
Search pattern: `ConversationManager`  
Scope: `cli-app/src/main/kotlin/**/*.kt`  
Total matches: 8 in 5 files

---

## cli-app/src/main/kotlin/llmchat/agent/ConversationManager.kt

**Line 25** — class declaration:
```kotlin
class ConversationManager(
```

---

## cli-app/src/main/kotlin/llmchat/Main.kt

**Line 27** — import:
```kotlin
import llmchat.agent.ConversationManager
```

**Line 338** — создание экземпляра в интерактивном режиме:
```kotlin
val conversationManager = ConversationManager(
```

**Line 1060** — создание экземпляра в headless-режиме:
```kotlin
val conversationManager = ConversationManager(
```

---

## cli-app/src/main/kotlin/llmchat/cli/CliParser.kt

**Line 84** — передача стратегии в конструктор:
```kotlin
strategyType = config.strategy, // передаётся в ConversationManager
```

---

## cli-app/src/main/kotlin/llmchat/support/SupportRunner.kt

**Line 19** — import:
```kotlin
import llmchat.agent.ConversationManager
```

**Line 182** — создание экземпляра в режиме поддержки:
```kotlin
val conversationManager = ConversationManager(
```

---

## cli-app/src/main/kotlin/llmchat/rag/TaskStateManager.kt

**Line 31** — интеграция с RAG-индексом:
```kotlin
// ConversationManager передаёт TaskState в RAG-пайплайн
```

---

## Summary

| File                     | Matches                            |
|--------------------------|------------------------------------|
| `ConversationManager.kt` | 1 (определение класса)             |
| `Main.kt`                | 3 (import + 2 создания экземпляра) |
| `CliParser.kt`           | 1 (передача конфигурации)          |
| `SupportRunner.kt`       | 2 (import + создание экземпляра)   |
| `TaskStateManager.kt`    | 1 (интеграция RAG)                 |

`ConversationManager` создаётся в **3 точках входа**: интерактивный CLI, headless-режим и support-режим. Каждая точка создаёт независимый экземпляр без общего состояния.
