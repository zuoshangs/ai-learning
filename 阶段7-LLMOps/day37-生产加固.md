# Day 37 — 生产加固（Production Hardening）

## 今日任务

| 项目 | 内容 |
|:-----|:-----|
| **集成全部** | 缓存 + 限流 + 可观测性 + 成本分析 → 统一生产网关 |
| **Docker 化** | 容器化部署 + docker-compose + Prometheus + Grafana |
| **K8s 清单** | Deployment + HPA + Service + Ingress + 三种探针 |
| **产出** | ✅ 生产级 AI API 服务 |

## 1. 生产网关架构

将前 4 天的 LLMOps 组件全部集成到一个统一的生产网关中：

```
用户请求 → 认证 → 限流 → 缓存 → 熔断器 → LLM → 指标 → 成本
           │      │      │       │        │      │      │
           │      │      │       │        │      │  成本分析(36)
           │      │      │       │        │   Micrometer(35)
           │      │      │       │    Circuit Breaker(33)
           │      │      │  Semantic Cache(34)
           │      │  Token Bucket(33)
        Auth Filter
```

### 代码结构

```
production-gateway/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── prometheus.yml
├── local.properties        # API Key（不提交 Git）
└── src/main/java/com/ai/llm/gateway/
    ├── ProductionGatewayApplication.java
    ├── config/
    │   ├── AppConfig.java          # Bean 工厂
    │   └── WebConfig.java          # CORS
    ├── filter/
    │   ├── AuthFilter.java          # 认证（@Order=1）
    │   ├── TraceFilter.java         # 链路追踪（@Order=0）
    │   └── RateLimitService.java    # Token Bucket 限流
    ├── cache/
    │   └── SemanticCacheService.java
    ├── circuit/
    │   └── CircuitBreakerService.java
    ├── cost/
    │   └── CostAnalyzerService.java
    ├── monitor/
    │   ├── MetricsService.java      # Micrometer 指标
    │   └── CustomHealthIndicator.java
    ├── llm/
    │   └── LlmProxyService.java     # 完整管道编排
    └── controller/
        ├── ChatController.java      # /api/chat
        └── AdminController.java     # /admin/*
```

## 2. 完整管道流程

`LlmProxyService.chat()` 的执行顺序：

```java
public ChatResult chat(String userMessage, String model, String apiKeyTier) {
    // 1. 启动计时器
    Timer.Sample sample = metrics.startTimer();
    metrics.recordRequest();

    // 2. 检查语义缓存（O(1) 精确 + TF-IDF 语义）
    CacheResult cached = checkCache(userMessage);
    if (cached != null) return cached;          // 零成本返回

    // 3. 检查熔断器
    if (!circuitBreaker.isAvailable()) {
        return "CIRCUIT_OPEN";                  // 快速失败
    }

    // 4. 调用 LLM
    LlmResponse response = callDeepSeek(prompt, actualModel);
    circuitBreaker.onSuccess();

    // 5. 记录成本（按模型/API key 统计）
    double cost = costAnalyzer.recordUsage(model, apiKeyTier, inputTokens, outputTokens);

    // 6. 记录指标
    metrics.recordTokens(model, inputTokens + outputTokens);
    metrics.recordCost(cost);

    // 7. 写入缓存（下次命中）
    cache.put(userMessage, response.content);
}
```

## 3. 关键组件

### 3.1 认证 + 限流（Filter 层）

| Filter | 顺序 | 功能 |
|--------|:----:|------|
| `TraceFilter` | 0 | 生成/透传 X-Trace-Id，写入 MDC |
| `AuthFilter` | 1 | 验证 X-API-Key → gold/silver/free 三级 |

Controller 层额外做 RateLimit：

```java
@PostMapping("/api/chat")
public ResponseEntity<?> chat(@RequestBody body, HttpServletRequest req) {
    // 从 AuthFilter 拿到 key
    String apiKey = (String) req.getAttribute("apiKey");
    
    // 限流（Token Bucket，30 rpm）
    if (!rateLimiter.tryAcquire(apiKey)) { return 429; }

    // 经过完整管道
    LlmProxyService.ChatResult result = llmProxy.chat(message, model, tier);
}
```

### 3.2 语义缓存

两层查询策略：

1. **精确匹配**：HashMap O(1)，命中直接返回
2. **语义匹配**：TF-IDF 向量化 + Cosine Similarity（阈值 0.85）

```java
public synchronized String get(String query) {
    // 1. 精确匹配
    CacheEntry entry = exactCache.get(query);
    if (entry != null) { return entry.response; }

    // 2. 语义匹配（TF-IDF 余弦相似度）
    double[] queryVec = tfidfVectorize(query);
    for (CacheEntry sem : semanticEntries) {
        if (cosineSimilarity(queryVec, sem.vector) >= threshold) {
            return sem.response;
        }
    }
    return null;
}
```

### 3.3 熔断器（3 状态）

```
CLOSED ──(5 次失败)──▶ OPEN ──(30s 超时)──▶ HALF_OPEN ──(成功)──▶ CLOSED
                        │                                              ↑
                        └──────────(探针失败)───────────────────────────┘
```

### 3.4 自定义健康检查

```json
{
  "status": "UP",
  "details": {
    "circuitBreaker": { "state": "CLOSED", "failures": 0 },
    "cache":           { "size": 128, "hitRate": "67.3%" },
    "rateLimiter":     { "defaultRpm": 30, "totalRequests": 512 },
    "metrics":         { "totalTokens": "15421", "totalCost": "$0.0152" }
  }
}
```

### 3.5 Calico 指标（Prometheus 格式）

```
# HELP gateway_requests_total Total requests
# TYPE gateway_requests_total counter
gateway_requests_total 256

# HELP gateway_tokens_total Total tokens consumed
# TYPE gateway_tokens_total counter
gateway_tokens_total 15421

# HELP gateway_cost_usd Total cost in USD
# TYPE gateway_cost_usd gauge
gateway_cost_usd 0.0152
```

## 4. Docker 化

### Dockerfile（多阶段构建）

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src src
RUN mvn package -q -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
HEALTHCHECK --interval=15s CMD wget -qO- http://localhost:8080/actuator/health
USER appuser
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
```

### docker-compose

```yaml
services:
  production-gateway:
    build: .
    ports: ["8080:8080"]
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
  
  prometheus:  # 抓取 metrics
    image: prom/prometheus
    ports: ["9090:9090"]

  grafana:     # 可视化仪表盘
    image: grafana/grafana
    ports: ["3000:3000"]
```

## 5. K8s 清单

| 资源 | 文件 | 关键配置 |
|------|------|----------|
| **Namespace** | `namespace.yaml` | llm-gateway |
| **ConfigMap** | `configmap.yaml` | 生产配置（application-prod.yml） |
| **Secret** | `configmap.yaml` | DEEPSEEK_API_KEY |
| **Deployment** | `deployment.yaml` | 2 replicas, RollingUpdate |
| **Service** | `service.yaml` | ClusterIP, port 80 → 8080 |
| **HPA** | `hpa.yaml` | CPU 70% / Memory 80%, min=2 max=10 |
| **Ingress** | `ingress.yaml` | TLS, path /api + /actuator |

### Deployment 探针配置

```yaml
startupProbe:    # 首次启动慢时给宽限
  httpGet: { path: /actuator/health/liveness }
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 30

livenessProbe:   # 存活检查（死锁检测）
  httpGet: { path: /actuator/health/liveness }
  periodSeconds: 15
  failureThreshold: 3

readinessProbe:  # 就绪检查（流量接入）
  httpGet: { path: /actuator/health/readiness }
  periodSeconds: 10
  failureThreshold: 2
```

### HPA（弹性伸缩）

```yaml
minReplicas: 2
maxReplicas: 10
metrics:
  - resource: { name: cpu, target: { type: Utilization, averageUtilization: 70 } }
  - resource: { name: memory, target: { type: Utilization, averageUtilization: 80 } }
behavior:
  scaleUp:
    stabilizationWindowSeconds: 60    # 1 分钟内可扩 2 副本
    policies: [{ type: Pods, value: 2, periodSeconds: 60 }]
  scaleDown:
    stabilizationWindowSeconds: 120   # 缩容更保守
    policies: [{ type: Pods, value: 1, periodSeconds: 120 }]
```

## 6. API 端点总览

| 端点 | 方法 | 需要 Key | 作用 |
|------|:----:|:--------:|------|
| `/actuator/health` | GET | ❌ | 健康检查（含所有组件详情） |
| `/actuator/prometheus` | GET | ❌ | Prometheus 指标 |
| `/api/chat` | POST | ✅ | 核心推理（经过全部管道） |
| `/api/cache/stats` | GET | ✅ | 缓存命中统计 |
| `/api/pipeline/status` | GET | ✅ | 各组件状态 |
| `/api/cost/report` | GET | ✅ | 成本报告 |
| `/api/cost/optimizations` | GET | ✅ | 优化建议 |
| `/admin/metrics` | GET | ❌ | 管理员指标概览 |
| `/admin/pricing` | GET | ❌ | 支持的模型定价表 |
| `/admin/users` | GET | ❌ | 按用户统计 Token |
| `/admin/cost` | GET | ❌ | 完整成本报告 |

## 7. 测试结果

```
=== Chat - Cache MISS ===
content: "关于'什么是LLM网关'的详细解答..."
fromCache: false | costUSD: 0.000360 | remainingTokens: 30

=== Chat - Cache HIT ===
content: (同上，从缓存返回)
fromCache: true | costUSD: 0.000000 | remainingTokens: 29

=== 管道状态 ===
CircuitBreaker: CLOSED | RateLimiter: 30rpm | Cache: 0.0%

=== 认证测试 ===
X-API-Key=invalid → 401 {"error":"Invalid API key"}
X-API-Key=缺失   → 401 {"error":"Missing X-API-Key header"}

=== 健康检查 ===
UP - Circuit: CLOSED | Cache: 128 entries | Rate: 30rpm
```

## 8. 部署命令速查

```bash
# 本地开发
mvn spring-boot:run

# Docker 构建
docker build -t production-gateway .
docker-compose up -d
docker-compose ps                   # 4 services: gateway + prometheus + grafana

# K8s 部署
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/ingress.yaml

# 监控资源
kubectl get pods -n llm-gateway -w
kubectl describe hpa production-gateway-hpa -n llm-gateway
kubectl port-forward svc/production-gateway 8080:80 -n llm-gateway
```

## 9. 关键设计决策

| 决策 | 理由 |
|------|------|
| **Filter 做认证+追踪，Controller 做业务** | 职责分离，Filter 不关心业务逻辑 |
| **`@Order` 控制 Filter 链顺序** | Trace(0) → Auth(1) → Controller(rate limit + pipeline) |
| **非对称 property 文件加载** | `spring.config.import=file:local.properties` 解决 env 变量传递问题 |
| **Micrometer SimpleMeterRegistry** | 无需外部存储，`/actuator/prometheus` 按需暴露 |
| **K8s 三种探针** | startup(30次重试) + liveness(3次) + readiness(2次)，渐进式失败 |
| **HPA 非对称伸缩** | 扩容激进(2 pods/60s) + 缩容保守(1 pod/120s) |
| **RollingUpdate maxUnavailable=0** | 保证至少一个副本始终可用 |

## 10. 总结

第 37 天将前面 4 天的 LLMOps 组件合并到一个统一生产网关：

| 组件 | Day 33 | Day 34 | Day 35 | Day 36 | Day 37 |
|------|:------:|:------:|:------:|:------:|:------:|
| Token Bucket 限流 | ✅ | | | | ✅ 集成 |
| 语义缓存 | | ✅ | | | ✅ 集成 |
| Micrometer 指标 | | | ✅ | | ✅ 集成 |
| 成本分析 | | | | ✅ | ✅ 集成 |
| 认证 + 追踪 | | | | | ✅ 新增 |
| Docker + K8s | | | | | ✅ 新增 |
| **生产管道** | ✅ | ✅ | ✅ | ✅ | **✅ 全部** |

### 文件清单

```
production-gateway/
├── pom.xml                                      # Maven 构建
├── Dockerfile                                   # 多阶段构建
├── docker-compose.yml                           # Prometheus + Grafana
├── prometheus.yml                               # 抓取配置
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── hpa.yaml
│   └── ingress.yaml
├── src/main/java/com/ai/llm/gateway/
│   ├── ProductionGatewayApplication.java        # 入口
│   ├── config/AppConfig.java + WebConfig.java   # Bean + CORS
│   ├── filter/
│   │   ├── TraceFilter.java                     # X-Trace-Id
│   │   ├── AuthFilter.java                      # API Key 认证
│   │   └── RateLimitService.java                # Token Bucket
│   ├── cache/SemanticCacheService.java          # 语义缓存
│   ├── circuit/CircuitBreakerService.java       # 熔断器
│   ├── cost/CostAnalyzerService.java            # 成本分析
│   ├── monitor/
│   │   ├── MetricsService.java                  # Micrometer 指标
│   │   └── CustomHealthIndicator.java           # 自定义健康检查
│   ├── llm/LlmProxyService.java                 # 管道编排
│   └── controller/
│       ├── ChatController.java                  # /api/chat
│       └── AdminController.java                 # /admin/*
```
