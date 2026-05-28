# Day 41：管理仪表盘 + LLMOps 集成 📊

> 在智能客服平台中集成全面的监控体系，包括实时指标收集、速率限制、响应缓存、成本追踪和可视化仪表盘。

---

## 1. 今日目标

将之前 Days 33-37 学习的 LLMOps 概念（限流、缓存、成本追踪、监控）集成到客服平台中，提供一个统一的管理仪表盘。

| 组件 | 说明 |
|:-----|:------|
| **MetricsCollector** | 集中式指标收集器，所有子系统的数据汇聚点 |
| **RateLimiter** | 令牌桶速率限制，防止滥用 |
| **ResponseCache** | 响应缓存，减少重复 LLM 调用 |
| **CostTracker** | 成本估算与追踪 |
| **DashboardService** | 聚合各子系统数据，生成仪表盘报告 |
| **DashboardController** | REST API 端点 |
| **dashboard.html** | 全功能仪表盘页面（暗色主题） |

---

## 2. 核心架构

```
用户请求 → RateLimiter 检查 → ResponseCache 检查 → LLM 调用
                                                    ↓
                                              CostTracker
                                                    ↓
用户响应 ← ConversationService ← MetricsCollector ←
                                       ↓
                                DashboardController → Dashboard HTML
```

所有组件通过 `MetricsCollector` 汇聚数据，`DashboardService` 聚合后通过 REST API 输出。

---

## 3. 核心实现

### 3.1 MetricsCollector（集中指标收集器）

```java
@Component
public class MetricsCollector {
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalLlmCalls = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCostMicros = new AtomicLong(0);
    private final List<Long> recentResponseTimes = ...;

    public Map<String, Object> getDashboardReport() {
        // 返回所有指标的聚合 JSON
    }
}
```

**核心设计：** 所有子系统通过方法调用记录事件，`MetricsCollector` 在内存中维护原子计数器，`getDashboardReport()` 生成完整报告。

### 3.2 RateLimiter（令牌桶）

```java
@Component
public class RateLimiter {
    private final int capacity;      // 桶容量
    private final double refillRate; // 每秒补充令牌数
    private final ConcurrentHashMap<String, Bucket> buckets = ...;

    public boolean allowRequest(String key) {
        // 获取或创建用户令牌桶
        // 补充令牌
        // 如果令牌 ≥ 1 则消耗并放行
        // 否则拒绝
    }
}
```

**原理：** 每个会话/IP 一个令牌桶，`capacity=20` 允许突发，`refillRate=5/s` 控制平均速率。

### 3.3 ResponseCache（响应缓存）

```java
@Component
public class ResponseCache {
    public String get(String query) {
        String key = normalizeKey(query);  // 归一化+哈希
        CacheEntry entry = cache.get(key);
        if (entry == null || expired(entry)) return null;
        return entry.response;
    }

    public void put(String query, String response) {
        // 存储，如果超过 maxSize 淘汰最旧
    }
}
```

**归一化策略：** 转小写 → 去标点 → 合并空白 → 截断至 100 字 → SHA-256 哈希。相同语义的查询命中同一缓存。

### 3.4 CostTracker（成本追踪）

| 模型 | 输入 (per 1M tokens) | 输出 (per 1M tokens) |
|:-----|:--------------------:|:--------------------:|
| deepseek-chat | $0.27 | $1.10 |
| deepseek-reasoner | $0.55 | $2.19 |

```java
public void trackCall(String prompt, String response, String model) {
    // 估算 Token（中文 1字≈1.5 token，英文 1字≈0.25 token）
    int inputTokens = estimateTokens(prompt);
    int outputTokens = estimateTokens(response);
    metricsCollector.recordCost(inputTokens, outputTokens, model);
}
```

---

## 4. 仪表盘页面

**前端设计：** 暗色主题，4 个标签页（概览/对话/成本/系统），实时刷新（10 秒轮询）。

### 4.1 统计卡片行

| 卡片 | 指标 | 颜色 |
|:----|:-----|:----:|
| 活跃会话 | `chat.activeSessions` | 🟦 蓝色 |
| 待处理工单 | `tickets.pending + inProgress` | 🟧 橙色 |
| 缓存命中率 | `cache.hitRate` | 🟩 绿色 |
| 知识库文档 | `knowledge.totalDocs` | 🟪 紫色 |
| 总消息数 | `chat.totalMessages` | 🩷 粉色 |
| 总成本 | `cost.totalCostCents` | 🟦 青色 |

### 4.2 概览标签页

4 个面板并行展示：
- **对话监控** — 会话数、消息数、LLM 调用、平均响应时间
- **工单概览** — 带迷你进度条的状态分布（待处理/处理中/已解决/已关闭）
- **缓存效率** — 命中/未命中迷你条 + 命中率百分比
- **限流监控** — 已放行/已限流比率

### 4.3 成本标签页

- **累计成本** — USD/人民币双显示
- **小时成本趋势** — 柱状图可视化最近 24 小时成本分布
- **按模型统计** — 各模型成本占比

### 4.4 系统标签页

- **系统信息** — 服务名称、运行时长、线程数
- **内存使用** — 带颜色指示条的迷你进度条
- **管理操作** — 清除缓存按钮

---

## 5. ConversationService 集成

修改后的流程：

```java
public ChatResponse processMessage(String sessionId, String message) {
    // 1. 速率限制检查
    if (!rateLimiter.allowRequest(sessionId)) {
        return rateLimitedResponse();
    }

    // 2. 缓存检查
    String cached = responseCache.get(message);
    if (cached != null) {
        return cachedResponse(sessionId, cached);
    }

    // 3. LLM 调用
    String reply = callLLM(prompt);

    // 4. 缓存结果
    responseCache.put(message, reply);

    // 5. 追踪成本
    costTracker.trackCall(prompt, reply, model);

    // 6. 记录指标
    metricsCollector.recordLlmCall(elapsed, tokens, model);

    return new ChatResponse(sid, reply, ...);
}
```

---

## 6. 测试结果

### 6.1 API 端点测试

| 端点 | 状态 | 说明 |
|:----|:----:|:-----|
| `GET /api/dashboard/health` | ✅ | 返回服务状态 UP |
| `GET /api/dashboard/status` | ✅ | 快速状态摘要 |
| `GET /api/dashboard/report` | ✅ | 完整仪表盘报告 |
| `GET /api/dashboard/metrics` | ✅ | 纯指标数据 |
| `POST /api/dashboard/cache/clear` | ✅ | 清除缓存 |
| `GET /dashboard` | ✅ | 仪表盘页面 |

### 6.2 Python 演示

```
✅ 18/18 全部通过
```

### 6.3 实时仪表盘数据（截图）

启动后仪表盘显示：
- 4 条初始工单（2 待处理，1 处理中，1 已解决）
- 7 篇知识库文档（6 个分类）
- 系统启动时间、内存使用、线程数
- 缓存命中率、成本追踪（实际 LLM 调用时更新）

---

## 7. 关键技术决策

| 决策 | 方案 | 理由 |
|:----|:-----|:-----|
| 存储方式 | 内存（非 DB） | 演示项目，重启即重置，无需持久化 |
| 缓存策略 | 归一化 + SHA-256 | 安全防碰撞，O(1) 查找 |
| 限流算法 | 令牌桶 | 支持突发流量，平均速率可控 |
| 成本精度 | 微美分 (µ¢) | 避免浮点误差，输出时格式化 |
| 前端刷新 | 10s 轮询 | 简单可靠，适合演示场景 |
| 前端主题 | 暗色 | 美观专业，适合监控场景 |

---

## 8. 明日预告：Day 42 🚀

- 集成测试：验证全平台功能
- Docker Compose 多服务部署
- CI/CD 流水线配置
- 项目总结

---

*"没有度量，就没有管理。LLMOps 的核心是让每一次 LLM 调用都可见、可度量、可优化。"*
