#!/bin/bash
# Quick Start Script for LLM Chat CLI

set -e

echo "=== LLM Chat CLI - Quick Start ==="
echo ""

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
echo ""
echo "Building application..."
./gradlew build --console=plain -q

echo ""
echo "✅ Build successful!"
echo ""
echo "Starting LLM Chat CLI..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Run the JAR directly for best interactive experience
java -jar build/libs/simple-llm-sample-1.0-SNAPSHOT.jar
