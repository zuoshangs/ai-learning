#!/bin/bash
# Startup script for LLM Gateway — sources all needed env vars
set -a
source ~/.hermes/.env 2>/dev/null || true
set +a

cd ~/ai-learning/阶段7-LLMOps/code/day33/llm-gateway
exec mvn spring-boot:run "$@"
