# Day 35 — 可观测性 + 指标监控

> **阶段**：阶段7-LLMOps  
> **目标**：用 Micrometer + Prometheus 构建 LLM 服务的可观测性  
> **日期**：2026-05-28

---

## 1. 今日学习内容

### 1.1 什么是可观测性

可观测性 (Observability) 三大支柱：

| 支柱 | 说明 | LLM 场景示例 |
|------|------|-------------|
| **Metrics** | 聚合数值指标 | 请求量、延迟 p50/p95/p99、错误率、Token 用量 |
| **Logging** | 结构化事件记录 | Trace ID 贯穿的日志、错误栈、慢查询日志 |
| **Tracing** | 请求链路追踪 | 一次 LLM 调用经历的 Auth→Cache→LLM 各阶段耗时 |

### 1.2 Micrometer 指标类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **Counter** | 只增不减 | 请求总数、Token 总数 |
| **Timer** | 延迟分布 | LLM 调用延迟、缓存查询延迟 |
| **Gauge** | 可上可下的瞬时值 | 缓存大小、最后延迟、峰值延迟 |
| **DistributionSummary** | 分布统计 | Token 用量分布 |

### 1.3 架构

```
用户请求 → TraceFilter (添加Trace ID)
    ↓
LlmController → LlmProxyService → LLM (模拟)
    ↓               ↓
MetricsCollector    Cache
(Counter/Timer)     (命中/未命中)
    ↓
Micrometer → Prometheus → Grafana / Dashboard
```

---

## 2. 代码实现

### 项目结构

```
code/day35/observability/
├── pom.xml
├── requirements.txt
├── observability_demo.py           # Python 版
└── src/main/java/com/ai/learning/obs/
    ├── ObservabilityApplication.java
    ├── config/
    │   └── MetricsConfig.java
    ├── metrics/
    │   └── LlmMetrics.java          # 自定义指标收集器
    ├── health/
    │   ├── LlmHealthIndicator.java   # LLM 健康检查
    │   └── MetricsHealthIndicator.java
    ├── tracing/
    │   └── TraceFilter.java          # 请求追踪
    ├── model/
    │   ├── MetricPoint.java
    │   └── ServiceHealth.java
    ├── service/
    │   ├── LlmProxyService.java      # 模拟 LLM 服务
    │   └── SimulationService.java    # 自动生成指标
    └── controller/
        ├── LlmController.java
        └── DashboardController.java
```

### 2.1 自定义指标收集 (Java)

```java
@Component
public class LlmMetrics {
    // Counters
    private final Counter totalRequests;
    private final Counter totalTokens;
    private final Counter totalErrors;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter rateLimitBlocks;

    // Timers
    private final Timer latency;  // 自动计算 p50/p95/p99

    // Gauges
    private final AtomicLong lastLatencyMs;
    private final AtomicLong peakLatencyMs;

    // 初始化时注册到 Micrometer
    public LlmMetrics(MeterRegistry registry) {
        totalRequests = Counter.builder("llm.requests.total")
                .description("Total LLM requests")
                .register(registry);
        // ... 其他指标类似
    }
}
```

### 2.2 请求追踪 (Java)

```java
@Component
@Order(0)
public class TraceFilter implements Filter {
    @Override
    public void doFilter(request, response, chain) {
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();

        MDC.put("traceId", traceId);       // 注入日志
        res.setHeader("X-Trace-Id", traceId); // 返回客户端

        try { chain.doFilter(request, response); }
        finally { MDC.remove("traceId"); }
    }
}
```

### 2.3 健康检查 (Java)

```java
@Component
public class LlmHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 轻量检查: TCP 连接 LLM API
        // 成功 → UP, 失败 → DOWN
        return Health.up()
                .withDetail("endpoint", "api.deepseek.com")
                .build();
    }
}
```

### 2.4 Prometheus 指标 (Python)

```python
class MetricsCollector:
    def to_prometheus(self) -> str:
        return f"""
# HELP llm_requests_total Total LLM requests
# TYPE llm_requests_total counter
llm_requests_total {snap['totalRequests']}

# HELP llm_latency_seconds LLM request latency
# TYPE llm_latency_seconds summary
llm_latency_seconds{{quantile="0.5"}} {p50}
llm_latency_seconds{{quantile="0.95"}} {p95}
llm_latency_seconds{{quantile="0.99"}} {p99}
        """
```

---

## 3. API 端点

| 端点 | 说明 |
|------|------|
| `/api/v1/chat` | LLM 对话（生成指标） |
| `/api/v1/ping` | 心跳 |
| `/api/dashboard/health` | 聚合健康状态 |
| `/api/dashboard/metrics` | 指标快照 |
| `/api/dashboard/stats` | 结构化统计 |
| `/api/dashboard/reset` | 重置指标 |
| `/actuator/health` | Spring Boot 健康 |
| `/actuator/metrics` | Micrometer 指标 |
| `/actuator/prometheus` | Prometheus 格式 |

---

## 4. 测试结果

```
✅ Ping: pong
✅ Dashboard Health: UP (uptime 40s)
✅ 自定义指标: requests=22, errors=1, hitRate=15.8%
✅ 自定义延迟指标: last=0ms, peak=971ms, p50=475ms
✅ 错误分布: auth_error=1
✅ Prometheus 指标: 14 个自定义指标可见
✅ Trace ID: 返回 X-Trace-Id 头
✅ Chat: 正确响应
```

### Prometheus 暴露的指标

```
llm_requests_total      22   请求总数
llm_errors_total        1    错误数
llm_cache_hits_total    4    缓存命中
llm_cache_misses_total  16   缓存未命中
llm_latency_seconds     [p50=0.42, p95=0.94, p99=0.97]  延迟分布
llm_latency_seconds_max 0.971  最大延迟
llm_tokens_total        2477  Token 总数
llm_ratelimit_blocks_total 1  限流次数
```

---

## 5. 关键技术决策

### 5.1 为什么用 Micrometer + Prometheus

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Micrometer** | Spring Boot 原生，统一 API | 需要了解 Micrometer 概念 |
| **Prometheus** | 开源标准，Grafana 生态 | 需要部署 |
| **自定义 Logger** | 简单 | 不可查询，无聚合 |
| **Datadog/APM** | 开箱即用 | 付费，依赖第三方 |

### 5.2 Counters vs Gauges

- **Counter**：累计值（只增不减）。适合请求总数、Token 总数。重置时新建 Counter。
- **Gauge**：瞬时值（可上可下）。适合当前缓存大小、最后延迟。

### 5.3 Trace ID 注入

用 `MDC` (Mapped Diagnostic Context) 把 Trace ID 注入到日志中。在 `logback.xml` 中配置 `%X{traceId}` 即可在每个日志行显示。

---

## 6. 陷阱与解决

1. **`Map.of()` 参数上限**：10 个键值对后需改用 `new HashMap<>()`
2. **Timer 百分位计算**：`publishPercentiles(0.5, 0.95, 0.99)` 需要足够样本才能准确
3. **Health Indicator 连锁影响**：LLM 不可用时整个 actuator 状态变为 DOWN → 可用 `HealthIndicator` 自定义状态
4. **模拟数据 vs 真实数据**：演示用随机延迟生成，真实场景需要实际 LLM 调用耗时

---

## 7. 扩展思考

- **Grafana 面板**：将 Prometheus 指标接入 Grafana 制作实时仪表盘
- **告警规则**：错误率 > 10% 时触发告警
- **分布式追踪**：接入 OpenTelemetry/Jaeger 实现跨服务追踪
- **SLI/SLO**：定义服务等级指标（延迟 p99 < 2s，错误率 < 1%）并持续监控
- **日志聚合**：ELK/Loki 收集结构化日志
