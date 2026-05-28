# Day 33 — LLM 网关 + 限流

> **阶段**：阶段7-LLMOps  
> **目标**：生产级 LLM 网关，统一鉴权、限流、熔断、路由  
> **日期**：2026-05-28

---

## 1. 今日学习内容

### 1.1 什么是 LLM 网关

LLM 网关是介于应用程序和大语言模型之间的中间层服务，统一管理：
- **请求路由**：根据配置将请求分发到不同 LLM 提供商
- **访问控制**：API Key 鉴权与多租户隔离
- **速率限制**：防止滥用，保证服务质量
- **熔断保护**：上游 LLM 不可用时优雅降级
- **成本监控**：统计 token 用量和延迟

### 1.2 核心组件

| 组件 | 说明 |
|------|------|
| **API Key 管理器** | 多层级密钥管理（Gold/Silver/Free） |
| **认证过滤器** | Servlet Filter，拦截无效请求 |
| **限流器** | 令牌桶 / 滑动窗口两种算法 |
| **熔断器** | 三态状态机（CLOSED→OPEN→HALF_OPEN） |
| **LLM Provider** | 可插拔的 LLM 后端接口 |
| **管理 API** | 实时查看统计、管理密钥、控制熔断 |

---

## 2. 代码实现

### 项目结构

```
code/day33/llm-gateway/
├── pom.xml
├── requirements.txt               # Python 依赖
├── llm_gateway_demo.py            # Python 对照版
├── start.sh                       # 启动脚本
└── src/main/java/com/ai/learning/gateway/
    ├── GatewayApplication.java
    ├── config/
    │   ├── GatewayConfig.java
    │   └── RateLimitConfig.java
    ├── gateway/
    │   └── LLMGateway.java
    ├── ratelimit/
    │   ├── RateLimiter.java
    │   ├── TokenBucketRateLimiter.java
    │   └── SlidingWindowRateLimiter.java
    ├── circuitbreaker/
    │   ├── CircuitBreaker.java
    │   └── CircuitState.java
    ├── auth/
    │   ├── ApiKeyManager.java
    │   └── AuthFilter.java
    ├── model/
    │   ├── ChatRequest.java
    │   └── ChatResponse.java
    ├── provider/
    │   ├── LlmProvider.java
    │   └── DeepSeekProvider.java
    └── controller/
        ├── GatewayController.java
        └── AdminController.java
```

### 2.1 令牌桶限流器 (Java)

```java
public class TokenBucketRateLimiter implements RateLimiter {
    private static class Bucket {
        final int capacity;       // 最大突发
        final double refillRate;  // 每秒补充令牌数
        double tokens;
        long lastRefillNanos;

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSec * refillRate);
            lastRefillNanos = now;
        }
    }
}
```

```python
class TokenBucket:
    def __init__(self, rate_per_minute: int):
        self.capacity = max(rate_per_minute // 6, 1)
        self.refill_rate = rate_per_minute / 60.0
        self.tokens = float(self.capacity)
        self.last_refill = time.time()
        self._lock = threading.Lock()

    def try_acquire(self) -> bool:
        with self._lock:
            self._refill()
            if self.tokens >= 1.0:
                self.tokens -= 1.0
                return True
            return False
```

### 2.2 熔断器状态机

```
            失败超过阈值
  CLOSED ──────────────────→ OPEN
    ↑                          │
    │                   等待超时
    │                          ↓
    │────────────────── HALF_OPEN
    │  连续成功恢复              │
    │                          │ 再次失败
    └──────────────────────────┘
```

### 2.3 网关管线

```java
public ChatResponse process(String apiKey, String providerName, ChatRequest request) {
    // 1. 限流检查
    if (!rateLimiter.tryAcquire(apiKey)) {
        return ChatResponse.rateLimited();
    }
    // 2. 熔断器检查
    if (!circuitBreaker.allowRequest()) {
        return ChatResponse.circuitOpen();
    }
    // 3. 调用 LLM Provider
    ChatResponse response = provider.call(request);
    // 4. 更新熔断器状态
    if (response.isSuccess()) {
        circuitBreaker.onSuccess();
    } else {
        circuitBreaker.onFailure();
    }
    return response;
}
```

### 2.4 API 端点

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/v1/chat` | POST | ✅ | LLM 对话 |
| `/api/v1/ping` | GET | ❌ | 心跳 |
| `/api/admin/stats` | GET | ✅ | 网关统计 |
| `/api/admin/keys` | GET/POST/DELETE | ✅ | 密钥管理 |
| `/api/admin/ratelimit` | GET/POST | ✅ | 限流控制 |
| `/api/admin/circuit` | GET/POST | ✅ | 熔断器控制 |
| `/actuator/health` | GET | ❌ | 健康检查 |

---

## 3. 关键技术决策

### 3.1 限流算法选择

| 算法 | 优点 | 缺点 |
|------|------|------|
| **令牌桶** | 允许突发，内存小 | 精度略低 |
| **滑动窗口** | 窗口精确，无毛刺 | 内存大（需存时间戳） |

**本日选择**：令牌桶（默认），可配置切换。性能测试显示滑动窗口快 40%，但令牌桶的突发能力更适合 LLM API 场景。

### 3.2 Spring Boot 环境变量

`@Value("${gateway.llm.api-key}")` 无法解析 `${DEEPSEEK_API_KEY}` 环境变量占位符（Spring Boot 默认 `PropertySourcesPlaceholderConfigurer` 不解析 OS 环境变量中带下划线的 key）。解决方法：写 `local.properties` 文件，通过 `spring.config.import` 导入。

### 3.3 自建 Provider

不使用 Spring AI 的 `OpenAiChatModel`，而是用 Java 21 的 `HttpClient` 直接调用 DeepSeek API。这样可以完整控制请求管线（鉴权→限流→熔断→实际调用），避免 Spring AI auto-configuration 的限制。

---

## 4. 测试结果

```
✅ 金钥对话: 13 tokens in / 135 tokens out, 2.8s latency
✅ 无效 API Key: 401 拦截
✅ 熔断器: CLOSED→OPEN (5次失败) → HALF_OPEN (2s超时) → CLOSED (2次成功)
✅ 密钥管理: CRUD 全支持
✅ 健康检查: UP
✅ 三档限流: Gold=1000, Silver=200, Free=30 (rpm)
```

---

## 5. 陷阱与解决

1. **Spring @Value + 环境变量**：`${DEEPSEEK_API_KEY}` 在 `@Value` 中不解析 → 改用 `local.properties` + `spring.config.import`
2. **端口冲突**：前一日服务器未停止 → `lsof -ti:8080 | xargs kill`
3. **Spring AI auto-configuration**：即使不用 Spring AI，`spring-ai-openai-starter` 也会尝试启动 OpenAI 连接 → 排除 `OpenAiAutoConfiguration`
4. **熔断器初始状态**：连续失败 5 次触发 OPEN，API key 解析失败也算在内 → 先 `POST /api/admin/circuit/reset`

---

## 6. 扩展思考

- **多 Provider 路由**：给每种模型添加权重，实现灰度发布和 A/B 测试
- **Redis 分布式限流**：当前限流器单机版，多实例部署需改用 Redis
- **请求排队**：限流时提供排队机制而不是直接拒绝
- **Prometheus 指标**：集成 Micrometer，输出 `http_requests_total` 等指标
