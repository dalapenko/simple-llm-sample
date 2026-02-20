#!/bin/bash
# Quick Start Script for LLM Chat CLI

set -e

echo "=== LLM Chat CLI - Quick Start ==="
echo ""

# Default values
TEMPERATURE="1.0"
MODEL="gpt-4o-mini"

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --temperature)
            TEMPERATURE="$2"
            shift 2
            ;;
        --model)
            MODEL="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: ./start.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --temperature VALUE    Set LLM temperature (0.0-2.0, default: 1.0)"
            echo "  --model MODEL          AI model to use (default: gpt-4o-mini)"
            echo "                         Available models:"
            echo "                           gpt-4o-mini  - GPT-4o Mini (default)"
            echo "                           mistral-7b   - Mistral 7B Instruct"
            echo "                           qwen-2.5     - Qwen 2.5 72B Instruct"
            echo "                           gpt-4o       - GPT-4o"
            echo "  --help, -h            Show this help message"
            echo ""
            echo "Environment Variables:"
            echo "  OPENROUTER_API_KEY    Required: Your OpenRouter API key"
            echo ""
            echo "Examples:"
            echo "  ./start.sh"
            echo "  ./start.sh --temperature 0.7"
            echo "  ./start.sh --model gpt-4o"
            echo "  ./start.sh --model mistral-7b --temperature 0.5"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check if API key is set
if [ -z "$OPENROUTER_API_KEY" ]; then
    echo "❌ Error: OPENROUTER_API_KEY environment variable is not set."
    echo ""
    echo "Please set your OpenRouter API key:"
    echo "  export OPENROUTER_API_KEY='your-api-key-here'"
    echo ""
    echo "Get your API key from: https://openrouter.ai"
    exit 1
fi

echo "✅ API key found"
echo "🤖 Model: $MODEL"
echo "🌡️  Temperature: $TEMPERATURE"
echo ""
echo "Building application..."
./gradlew build --console=plain -q

echo ""
echo "✅ Build successful!"
echo ""
echo "Starting LLM Chat CLI..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Run the JAR with parameters
java -jar build/libs/simple-llm-sample-1.0-SNAPSHOT.jar --temperature "$TEMPERATURE" --model "$MODEL"
