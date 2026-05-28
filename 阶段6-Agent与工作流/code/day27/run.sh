#!/bin/bash
set -a
source ~/.hermes/.env 2>/dev/null
set +a
cd /home/zuoshangs/ai-learning/阶段6-Agent与工作流/code/day27
exec mvn spring-boot:run
