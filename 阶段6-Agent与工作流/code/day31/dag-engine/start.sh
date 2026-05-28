#!/bin/bash
source ~/.hermes/.env
export DEEPSEEK_API_KEY
cd ~/ai-learning/阶段6-Agent与工作流/code/day31/dag-engine
echo "Starting with DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY:0:10}..."
mvn spring-boot:run 2>&1