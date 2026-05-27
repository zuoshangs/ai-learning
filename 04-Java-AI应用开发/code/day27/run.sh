#!/bin/bash
set -a
source ~/.hermes/.env 2>/dev/null
set +a
cd /home/zuoshangs/ai-learning/04-Java-AI应用开发/code/day27
exec mvn spring-boot:run
