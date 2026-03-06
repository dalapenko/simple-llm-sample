# Тематика

Две связанные фичи на основе Task FSM:

1. **Transition Guard System** — принудительный контроль допустимых переходов: `PLAN_APPROVED` как обязательный контрольный пункт, плагинный интерфейс `TransitionGuard` для бизнес-правил
2. **Autonomous Task Transition** — LLM-агент самостоятельно управляет переходами через текстовый маркер `[TASK_TRANSITION]`; стадии с `USER_APPROVAL` (PLAN_APPROVED, VALIDATION, DONE) всегда требуют явного подтверждения

Обе фичи работают поверх одного графа переходов и одного FSM.

**Записи сессий**:
- [`manual-demo.gif`](manual-demo.gif) — Guard System: блокировка невалидного перехода `PLANNING → EXECUTION`, исправление через `PLAN_APPROVED`, пауза и восстановление
- [`automode-demo.gif`](automode-demo.gif) — Autonomous Mode: LLM предлагает `PLAN_APPROVED` (user approves) → предлагает `EXECUTION` (auto) → пауза → восстановление

---

## Граф переходов (общий для обоих режимов)

```
PLANNING ──→ PLAN_APPROVED ──→ EXECUTION ──→ VALIDATION ──→ DONE
    │               │               │               │
    └──→ ERROR ←────┴───────────────┴───────────────┘
         │
         └──→ PLANNING
```

`PLAN_APPROVED` — структурный обязательный шаг. Прямой путь `PLANNING → EXECUTION` не существует ни в ручном, ни в автономном режиме.

### Допустимые переходы

|       Из \ В        | PLANNING | PLAN_APPROVED | EXECUTION | VALIDATION | DONE | ERROR |
|:-------------------:|:--------:|:-------------:|:---------:|:----------:|:----:|:-----:|
|    **PLANNING**     |    —     |       ✅       |     —     |     —      |  —   |   ✅   |
|  **PLAN_APPROVED**  |    ✅     |       —       |     ✅     |     —      |  —   |   ✅   |
|    **EXECUTION**    |    ✅     |       —       |     —     |     ✅      |  —   |   ✅   |
|   **VALIDATION**    |    ✅     |       —       |     ✅     |     —      |  ✅   |   ✅   |
|      **DONE**       |    —     |       —       |     —     |     —      |  —   |   —   |
|      **ERROR**      |    ✅     |       —       |     —     |     —      |  —   |   —   |

---

## Часть 1 — Ручной режим: Transition Guard System

**Модель**: GPT-4o Mini · **Задача**: `Разработать систему аутентификации пользователей`

### Сценарий (сессия 1 — блокировка и исправление)

```
 1. /task start Разработать систему аутентификации пользователей
 2. /task status                          ← PLANNING
 3. [LLM] Как лучше хранить хэши паролей — bcrypt или argon2?
 4. /task advance execution               ← НЕВАЛИДНЫЙ переход
    ERROR: Allowed from PLANNING: PLAN_APPROVED, ERROR
 5. /task advance plan_approved           ← корректный переход PLANNING → PLAN_APPROVED
 6. /task status                          ← Stage: Plan Approved
 7. /task advance execution               ← теперь разрешён: PLAN_APPROVED → EXECUTION
 8. /task step Реализуем хэширование паролей через bcrypt
 9. /task pause / /exit
```

### Блокировка невалидного перехода ✅

```
/task advance execution
 ERROR Invalid transition: PLANNING → EXECUTION. Allowed from PLANNING: PLAN_APPROVED, ERROR
```

FSM вернул `Result.failure()` — состояние не изменилось, история не обновилась. Сообщение об ошибке содержит список допустимых переходов — обучающий сигнал, а не просто отказ.

### Исправление маршрута ✅

```
/task advance plan_approved
  Task: Planning → Plan Approved   (Transitions: 1)

/task advance execution
  Task: Plan Approved → Execution  (Transitions: 2)
```

### Сессия 2 — восстановление ✅

```
 ⟳ Resuming Task
──────────────────────────────────────────────────
  Task:  Разработать систему аутентификации пользователей
  Stage: Execution
  Step:  Реализуем хэширование паролей через bcrypt
──────────────────────────────────────────────────

 Resume task? [y/N]: y
```

После перезапуска `Stage: Execution`, `Transitions: 2` — история переходов пережила перезапуск. Блок `[ACTIVE TASK]` инжектируется в системный промпт каждого запроса без пересказа.

### Механизм Guard

**`GuardResult`** — типобезопасный результат проверки:

```kotlin
sealed class GuardResult {
    data object Allow : GuardResult()
    data class Deny(val reason: String, val invariantId: String? = null) : GuardResult()
}
```

**`TransitionGuard`** — SAM-интерфейс, lambda-совместимый:

```kotlin
fun interface TransitionGuard {
    fun validate(from: TaskStage, to: TaskStage): GuardResult
}
```

**Порядок проверок в `TaskFSM.transition()`:**

```kotlin
// 1. Структурная проверка графа
val allowed = validTransitions[state.stage] ?: emptySet()
if (newStage !in allowed) return Result.failure(...)

// 2. Бизнес-правила через guards
for (guard in guards) {
    val result = guard.validate(state.stage, newStage)
    if (result is GuardResult.Deny) return Result.failure(...)
}

// 3. Мутация состояния + запись в history[]
state = state.copy(stage = newStage, history = state.history + record)
```

Если переход заблокирован — состояние **не изменяется**, история **не обновляется**.

### Статистика токенов (ручной режим)

| Сессия | Запрос                          | input | ctx | output | total |
|:------:|---------------------------------|:-----:|:---:|:------:|:-----:|
|   1    | bcrypt vs argon2 (планирование) |  ~12  | ~0  |  ~117  | ~129  |

---

## Часть 2 — Автономный режим: Autonomous Task Transition

**Модель**: GPT-4o Mini · **Задача**: `Разработать REST API с JWT-аутентификацией`

### Сценарий (сессия 1 — автономный режим)

```
 1. /task start Разработать REST API с JWT-аутентификацией
 2. /task status                          ← PLANNING
 3. /task auto on                         ← включаем автономный режим
 4. [LLM] Составь детальный план...      ← LLM → предлагает PLAN_APPROVED
    → ⟳ Task Transition Proposed
      Target: Plan Approved | Approval required.
    → Apply transition? [y/N]: y          ← пользователь подтверждает
    → Task: Planning → Plan Approved
 5. /task status                          ← Stage: Plan Approved
 6. [LLM] Создай заготовку JWT-контроллера ← LLM → предлагает EXECUTION (auto)
    → ⟳ Task Transition Proposed
      Target: Execution                   ← нет запроса — auto-applied
    → Task: Plan Approved → Execution
 7. /task status                          ← Stage: Execution
 8. /task pause / /exit
```

### Сессия 2 — восстановление ✅

```
 ⟳ Resuming Task
──────────────────────────────────────────────────
  Task:  Разработать REST API с JWT-аутентификацией
  Stage: Execution
──────────────────────────────────────────────────

 Resume task? [y/N]: y

/task auto
  Autonomous mode: OFF   ← сброс после перезапуска — безопасный дефолт
```

### Протокол [TASK_TRANSITION]

LLM добавляет маркер в **конец** ответа:

```
[TASK_TRANSITION]
to: PLAN_APPROVED
step: Составлен детальный план разработки REST API с JWT-аутентификацией.
reason: Планы детализированы и охватывают все ключевые аспекты разработки.
[/TASK_TRANSITION]
```

`ConversationManager` парсит, стрипает из ответа **до сохранения в историю** — LLM не видит собственные предложения в следующих турах:

```kotlin
val rawResponse = agent.run(userMessage)
val proposal = if (autoMode) parseTransitionProposal(rawResponse) else null
val response = if (proposal != null) stripTransitionMarker(rawResponse) else rawResponse
strategy.addMessage(userMessage, response)   // маркер не попадает в историю
```

### Approval Policy

| Стадия (target) |  `requiredApproval`  | Поведение в авто-режиме         |
|:---------------:|:--------------------:|:--------------------------------|
|    PLANNING     |   `LLM_GENERATION`   | Применяется автоматически       |
|  PLAN_APPROVED  |   `USER_APPROVAL`    | CLI спрашивает "Apply? [y/N]:"  |
|    EXECUTION    |   `LLM_GENERATION`   | Применяется автоматически       |
|   VALIDATION    |   `USER_APPROVAL`    | CLI спрашивает "Apply? [y/N]:"  |
|      DONE       |   `USER_APPROVAL`    | CLI спрашивает "Apply? [y/N]:"  |
|      ERROR      |   `LLM_GENERATION`   | Применяется автоматически       |

Даже в автономном режиме `PLAN_APPROVED` невозможно пройти без явного `y` от пользователя — структурный контрольный пункт сохраняется.

### Статистика токенов (автономный режим)

| Сессия | Запрос                             | input | ctx  | output | total |
|:------:|------------------------------------|:-----:|:----:|:------:|:-----:|
|   1    | план разработки (→ PLAN_APPROVED)  |  ~22  |  ~0  |  ~433  | ~455  |
|   1    | JWT-контроллер (→ EXECUTION)       |  ~27  | ~455 |  ~275  | ~757  |

`ctx: ~0` — первый запрос, история пуста. Блок `[ACTIVE TASK]` находится в системном промпте и не учитывается в счётчике `ctx`.

---

## Сравнение режимов

| Аспект                         | Ручной (`/task advance`)                               | Автономный (`/task auto on`)                      |
|--------------------------------|--------------------------------------------------------|---------------------------------------------------|
| **Инициатор перехода**         | Пользователь командой                                  | LLM через `[TASK_TRANSITION]` маркер              |
| **Блокировка FSM**             | Ошибка с подсказкой допустимых переходов               | Ошибка, proposal игнорируется                     |
| **PLAN_APPROVED**              | Обязателен — без него `/task advance execution` упадёт | Обязателен + требует `y` от пользователя          |
| **EXECUTION**                  | Явная команда после PLAN_APPROVED                      | Auto-applied без approval                         |
| **История переходов**          | Пишется в `history[]` после каждой команды             | Пишется в `history[]` после каждого proposal      |
| **Персистентность авто-флага** | —                                                      | `autoMode` не сохраняется — OFF после перезапуска |
| **Трассируемость**             | Команда видна в терминале                              | Proposal всегда отображается до применения        |
| **Сложность интеграции**       | Нулевая — встроено изначально                          | Один regex, один флаг в ConversationManager       |

---

## Плюсы и минусы

### Guard System (оба режима)

| Аспект                              | Описание                                                                                                       |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------|
| **Compile-time безопасность**       | `sealed class GuardResult` — компилятор требует обработки `Allow` и `Deny`                                     |
| **Декларативный граф**              | `Map<TaskStage, Set<TaskStage>>` — граф читается как таблица                                                   |
| **Расширяемость без изменений FSM** | Новые правила добавляются через `guards: List<TransitionGuard>`                                                |
| **Атомарность**                     | Заблокированный переход не мутирует состояние и не обновляет `history[]`                                       |
| **Traceability**                    | Каждый переход фиксируется с timestamp — полный маршрут задачи восстанавливаем                                 |
| **Обратная совместимость**          | `PLAN_APPROVED` сломал существующие тесты с `PLANNING → EXECUTION` — потребовалось обновление всех тестов      |

### Автономный режим (дополнительно)

| Аспект                                     | Описание                                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------|
| **Human-in-the-loop по политике**          | `USER_APPROVAL` стадии всегда требуют подтверждения — агент не может их автоматизировать  |
| **Маркер стрипается до истории**           | LLM не видит собственные `[TASK_TRANSITION]` — нет риска рекурсивных предложений          |
| **LLM может не добавить маркер**           | Если агент не считает стадию завершённой — переход не предлагается (корректное поведение) |
| **LLM может предложить невалидную стадию** | FSM заблокирует, пользователь увидит ошибку                                               |
| **Маркер в тексте — хрупкое решение**      | Regex-парсер; изменение форматирования LLM сломает парсинг                                |
| **autoMode не сериализуется**              | После каждого перезапуска нужно `/task auto on` — дополнительный шаг при длинных задачах  |

---

## Вывод

Transition Guard + Autonomous Mode — два уровня контроля поверх одного FSM:

1. **Граф** обеспечивает структурную корректность — `PLANNING → EXECUTION` невозможен физически
2. **Guards** позволяют добавлять бизнес-правила без изменений FSM
3. **Approval policy** разделяет переходы на автоматические и требующие человека
4. **`[TASK_TRANSITION]` маркер** даёт LLM минимальный структурный выход без изменений koog-пайплайна

Ключевой инсайт из ручного демо: сообщение `Allowed from PLANNING: PLAN_APPROVED, ERROR` превращает ошибку FSM в обучающий сигнал.
Ключевой инсайт из авто-демо: `PLAN_APPROVED` требует `y` от пользователя даже когда LLM полностью уверен — принудительный human-in-the-loop не отключается.
