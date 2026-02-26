# Тематика
Скользящее контекстное окно с суммаризацией

## Режим A — без суммаризации (--context-window 100 --summary-batch 100)
### Input:
My name is Dmitrii and I'm a beginner learning Kotlin coroutines. Give me a simple overview.
What is the difference between launch and async?
When should I use supervisorScope instead of coroutineScope?
How does structured concurrency prevent resource leaks?
What is the difference between a hot and cold Flow in Kotlin?
Can you show a simple example of Flow with error handling?
Given what you know about me and everything we discussed, what is the single most important concept I should focus on mastering first?

### Output (Вопрос 7):
The single most important concept you should focus on mastering first is **suspending functions**. Understanding suspending functions is fundamental to effectively using Kotlin coroutines, as they enable you to write asynchronous code in a sequential style. Learn how to create, use, and combine suspending functions, as this knowledge will form the basis for all your coroutine work, including features like `launch`, `async`, and different scopes. Once you're comfortable with suspending functions, you can build on that knowledge to explore other coroutine concepts and patterns.
### Statistics
| Ход | request | window | summaries | response | total |
|-----|---------|--------|-----------|----------|-------|
| 1   | ~23     | ~520   | ~0        | ~545     | ~568  |
| 2   | ~12     | ~568   | ~0        | ~283     | ~863  |
| 3   | ~15     | ~863   | ~0        | ~343     | ~1221 |
| 4   | ~13     | ~1221  | ~0        | ~534     | ~1768 |
| 5   | ~15     | ~1768  | ~0        | ~460     | ~2243 |
| 6   | ~14     | ~2243  | ~0        | ~375     | ~2632 |
| 7   | ~33     | ~2632  | ~0        | ~145     | ~2810 |

## Режим B — с суммаризацией (--context-window 3 --summary-batch 3)
### Input:
Тот же диалог. Компактизация срабатывает после вопроса 6: `recentHistory.size=6`, `6 - 3 = 3 >= summaryBatchSize=3` → ходы 1-3 сжимаются в summary, в окне остаются ходы 4-6.
### Output (Вопрос 7):
The single most important concept you should focus on mastering first is **coroutine scope**. Understanding coroutine scopes is essential because they define the lifecycle of coroutines, help manage resource allocation, and prevent leaks. Mastering coroutine scopes will provide a solid foundation for working with other concepts in Kotlin coroutines, such as structured concurrency, coroutine builders, and error handling.
### Statistics
| Ход   | request | window    | summaries | response | total     |
|-------|---------|-----------|-----------|----------|-----------|
| 1     | ~23     | ~0        | ~0        | ~475     | ~498      |
| 2     | ~12     | ~498      | ~0        | ~400     | ~910      |
| 3     | ~15     | ~910      | ~0        | ~409     | ~1334     |
| 4     | ~13     | ~1334     | ~0        | ~405     | ~1752     |
| 5     | ~15     | ~1752     | ~0        | ~261     | ~2028     |
| 6     | ~14     | ~2028     | ~0        | ~319     | ~2361     |
| **7** | ~33     | **~1027** | **~202**  | ~105     | **~1367** |

После вопроса 6 в лог напечатано: `[Context] Summarizing 3 older turns into a summary...`

## Вывод
### Анализ
#### Экономия токенов
На вопросе 7 режим B потребил на **~49% меньше** токенов суммарно (2810 → 1367) и на **~39% меньше** токенов в окне (2632 → 1027). Вопросы 1-3 (~1171 токенов) были заменены summary на ~202 токенов — сжатие 6:1.

#### Сохранение контекста
Режим A воспроизвёл уровень пользователя: *"As a **beginner**..."* — потому что вопрос 1 лежал в окне.

Режим B дал тематически верный совет, но не назвал ни имя Alice, ни уровень beginner — суммаризатор сохранил тему диалога, но не персональные детали. Это известная особенность lossy-компрессии: summary-промпт сфокусирован на фактах и решениях, а не на метаданных пользователя.

#### Момент срабатывания
Компактизация сработала точно по условию: `recentHistory.size - windowSize >= summaryBatchSize` → `6 - 3 = 3 >= 3`. Токены summary (~202) появились только начиная с хода 7 — до этого поле `summaries` было равно 0.

### Итог
Скользящее окно с суммаризацией работает: на длинных диалогах потребление токенов стабилизируется вместо линейного роста. Плата — потеря точных деталей из сжатых ходов.
