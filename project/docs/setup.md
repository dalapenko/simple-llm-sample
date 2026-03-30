# Установка и запуск LLM Chat CLI

## Требования

- Java 21+
- Gradle 8.x (обёртка включена: `./gradlew`)
- `OPENROUTER_API_KEY` — для облачных моделей (OpenRouter)
- Ollama — для локальных моделей

## Сборка

```bash
# Основное приложение
./gradlew :cli-app:shadowJar

# Git MCP сервер
./gradlew :mcp-server-git:shadowJar

# Все JAR за один раз
./gradlew shadowJar
```

## Запуск

### Облачная модель (OpenRouter)
```bash
export OPENROUTER_API_KEY='sk-...'
java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar
```

### Локальная модель (Ollama)
```bash
# Убедиться, что Ollama запущен
ollama serve

# Запустить CLI
java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar \
  --provider ollama \
  --local-model llama3.2
```

### С RAG (расширенный режим)
```bash
java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar \
  --provider ollama \
  --local-model llama3.2 \
  --rag advanced
```

## Developer Assistant Mode (пошаговая инструкция)

1. **Сборка серверов**
   ```bash
   ./gradlew :cli-app:shadowJar :mcp-server-git:shadowJar
   ```

2. **Запуск CLI с RAG**
   ```bash
   java -jar cli-app/build/libs/cli-app-1.0-SNAPSHOT-all.jar \
     --provider ollama --local-model llama3.2 --rag advanced
   ```

3. **Подключить Git MCP сервер**
   ```
   /mcp connect java -jar mcp-server-git/build/libs/mcp-server-git-1.0-SNAPSHOT-all.jar
   ```

4. **Проиндексировать документацию**
   ```
   /index project/docs structural
   ```

5. **Получить интеллектуальный обзор проекта**
   ```
   /help
   ```
   При активном RAG `/help` задаст LLM вопрос о проекте, получит контекст
   из документации и текущего состояния git, и вернёт развёрнутый ответ.

## Параметры запуска CLI

| Параметр               | Описание                                | Значение по умолчанию     |
|------------------------|-----------------------------------------|---------------------------|
| `--provider`           | Провайдер: `openrouter` / `ollama`      | `openrouter`              |
| `--local-model`        | Имя модели Ollama                       | `llama3.2`                |
| `--local-url`          | URL Ollama сервера                      | `http://localhost:11434`  |
| `--rag`                | Режим RAG: `basic`/`advanced`/`conversational` | отключён           |
| `--strategy`           | Контекстная стратегия                   | `sliding-window`          |
| `--system`             | Кастомный системный промпт              | встроенный                |
| `--temperature`        | Температура генерации                   | `1.0`                     |
| `--context-window`     | Размер окна контекста (сообщений)       | `20`                      |
