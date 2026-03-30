# Справочник команд LLM Chat CLI

## Основные команды

| Команда      | Описание                                                        |
|--------------|-----------------------------------------------------------------|
| `/help`      | Показать помощь; при активном RAG — умный обзор проекта через LLM |
| `/clear`     | Очистить историю диалога                                        |
| `/history`   | Показать историю сообщений                                      |
| `/strategy`  | Показать активную контекстную стратегию                         |
| `/exit`      | Выход из приложения                                             |

## Индексирование документации (RAG)

```bash
/index <путь>                        # индексировать директорию (структурная стратегия)
/index <путь> structural             # явно структурная разбивка
/index <путь> fixed                  # фиксированные блоки (по токенам)
/index <путь> --report               # сравнить обе стратегии
```

Пример: `/index project/docs structural`

## MCP (Model Context Protocol)

```bash
/mcp connect <команда> [аргументы]   # подключить MCP-сервер
/mcp tools                           # список доступных инструментов
/mcp status                          # статус всех подключений
/mcp disconnect                      # отключить все серверы
```

### Git MCP сервер
```bash
/mcp connect java -jar mcp-server-git/build/libs/mcp-server-git-1.0-SNAPSHOT-all.jar
```
Инструменты: `git_branch`, `git_status`, `git_diff`, `git_log`, `list_project_files`

## Память (стратегия `layered`)

```bash
/memory add short-term <данные>      # добавить в краткосрочную память
/memory add work <данные>            # рабочая память
/memory add long-term <данные>       # долгосрочная память (сохраняется между сессиями)
/memory list                         # все элементы всех слоёв
/memory list work                    # только рабочий слой
/memory delete work <id>             # удалить элемент
/memory clear short-term             # очистить слой
```

## Инварианты проекта

```bash
/invariant add <описание>                        # добавить общее ограничение
/invariant add --category stack <описание>        # технологический стек
/invariant add --category architecture <описание> # архитектурное решение
/invariant list                                   # все инварианты
/invariant remove <INV-XXXXXX>                    # удалить по ID
/invariant clear                                  # удалить все
```

## Задачи (FSM)

```bash
/task start <описание>     # начать отслеживание задачи
/task status               # текущий статус и стадия
/task advance <стадия>     # переход в стадию (planning/execution/validation/done)
/task auto on              # включить автоматические переходы
/task done                 # завершить задачу
/task cancel               # отменить задачу
```

## Ветки (стратегия `branching`)

```bash
/branch list               # список всех веток
/branch new <имя>          # создать и переключиться на новую ветку
/branch switch <имя>       # переключиться на существующую ветку
/checkpoint save <имя>     # сохранить чекпоинт
/checkpoint list           # список чекпоинтов
```

## Профиль

```bash
/profile                   # статус активного профиля
/profile path              # путь к файлу профиля
/profile reload            # перезагрузить профиль без рестарта
```
