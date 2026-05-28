# Day 36 — 性能调优 + 成本分析

> **阶段**：阶段7-LLMOps  
> **目标**：分析 LLM 调用成本 + 性能基准测试 + 生成优化建议  
> **日期**：2026-05-28

---

## 1. 今日学习内容

### 1.1 为什么需要成本分析

LLM API 调用是按量计费的，一个中等规模的应用每月可能产生数千美元的费用。成本分析帮助回答：

| 问题 | 答案 |
|------|------|
| 哪个模型最贵？ | GPT-4o 是 DeepSeek 的 18 倍 |
| 哪个用户消耗最多？ | 按 userId 分组统计 |
| 缓存节省了多少？ | 缓存命中率 → 实际节省 |
| 什么时候成本最高？ | 小时级趋势 → 峰值时段 |
| 哪里可以优化？ | 自动生成建议 |

### 1.2 模型定价对比

| 模型 | 输入 $/1K | 输出 $/1K | 500/200 Token 一次调用 |
|------|:---------:|:---------:|:--------------------:|
| **DeepSeek v4 Flash** | $0.00027 | $0.00110 | **$0.00036** |
| **DeepSeek Reasoner** | $0.00055 | $0.00219 | $0.00071 |
| **GPT-4o** | $0.00500 | $0.01500 | $0.00550 |
| **GPT-4o-mini** | $0.00015 | $0.00060 | $0.00020 |
| **Claude 3.5 Sonnet** | $0.00300 | $0.01500 | $0.00450 |
| **Qwen Turbo** | $0.00080 | $0.00200 | $0.00080 |

### 1.3 优化策略

| 策略 | 节省潜力 | 适用场景 |
|------|:--------:|---------|
| **语义缓存** | 50-80% | 重复/相似查询多的场景 |
| **模型降级** | 60-90% | 简单任务用便宜模型 |
| **请求合并** | 30-50% | 小 Token 量高频请求 |
| **批处理** | 20-40% | 非实时任务 |

---

## 2. 代码实现

### 项目结构

```
code/day36/cost-optimizer/
├── pom.xml
├── requirements.txt
├── cost_optimizer_demo.py
└── src/main/java/com/ai/learning/cost/
    ├── CostOptimizerApplication.java
    ├── model/
    │   ├── ModelCost.java        # 定价模型 + 费用计算
    │   └── UsageRecord.java      # 单次使用记录
    ├── service/
    │   ├── CostAnalyzer.java     # 成本分析引擎
    │   └── PerformanceBenchmark.java  # 性能基准测试
    └── controller/
        └── CostController.java   # REST API
```

### 2.1 成本计算 (Java)

```java
public class ModelCost {
    // 内置 9 个模型定价
    public static ModelCost forModel(String name) {
        return KNOWN_MODELS.getOrDefault(name, defaultPricing);
    }

    public CostResult calculate(int promptTokens, int completionTokens) {
        double usd = (promptTokens / 1000.0) * inputCostPer1K
                   + (completionTokens / 1000.0) * outputCostPer1K;
        return new CostResult(promptTokens, completionTokens, usd, cny);
    }
}
```

### 2.2 成本报告 (Java)

```java
public Map<String, Object> costReport(String userId, Duration period) {
    var filtered = records.stream()
        .filter(r -> r.getTimestamp().isAfter(since))
        .filter(r -> userId == null || userId.equals(r.getUserId()))
        .collect(Collectors.groupingBy(UsageRecord::getModel));
    // → 按模型汇总: 请求数、Token、费用
}
```

### 2.3 性能基准测试

```java
public BenchmarkResult runBenchmark(int concurrency, int totalRequests,
                                     int simulatedLatencyMs) {
    var barrier = new CyclicBarrier(concurrency); // 同时开始
    // 多线程运行
    // 统计 p50/p95/p99 延迟 + 吞吐量
}
```

---

## 3. API 端点

| 端点 | 说明 |
|------|------|
| `/api/cost/report` | 成本报告（可筛选用户和时间） |
| `/api/cost/trend` | 小时级成本趋势 |
| `/api/usage/simulate` | 模拟一次调用 |
| `/api/usage/simulate-batch` | 批量模拟 |
| `/api/optimize/suggestions` | 自动优化建议 |
| `/api/benchmark/run` | 运行性能基准测试 |
| `/api/benchmark/all` | 运行所有预设场景 |
| `/api/models/pricing` | 模型定价表 |

---

## 4. 测试结果

```
Java 版
✅ 模型定价表: 9 个模型，含美元和人民币
✅ 成本报告: 200 条记录 → $0.1455 / ¥1.04
✅ 按用户筛选: user-alice 费用
✅ 批量模拟: 20 条记录
✅ 基准测试: 10并发/30次/200ms → p50=200ms, 48rps
✅ 优化建议: 缓存优化建议 (cache 84%)
✅ 小时趋势: 实时数据展示

Python 版
✅ 5 大演示模块完整运行
✅ 成本趋势可视化 (字符柱状图)
✅ 性能基准测试 (4 场景)
✅ 优化建议自动生成
```

---

## 5. 关键技术决策

### 5.1 定价模型设计

用 `Map<String, ModelCost>` 静态字典存储已知模型价格，`forModel()` 兜底返回默认价格。这种设计：
- 新增模型只需加一行
- 不依赖外部 API
- 支持美元和人民币双币种

### 5.2 基准测试策略

用 `CyclicBarrier` 确保所有线程同时开始，模拟真实并发场景。`Thread.sleep()` 模拟 LLM 延迟，避免实际调用产生费用。

### 5.3 优化建议引擎

基于规则而非 ML：
- 缓存率 < 20% → 建议启用缓存
- 平均费用 > $0.003 → 建议降级模型
- 平均 Token < 300 → 建议合并请求

---

## 6. 陷阱与解决

1. **`Map.of()` 参数上限**：超过 10 个键值对时改用 `new HashMap<>()`（Java 9+ 限制）
2. **抽样数据的时间戳**：模拟数据都在启动时生成，导致小时趋势集中在当前小时 → 可通过伪造时间戳解决
3. **并发基准测试耗时**：`Thread.sleep()` 是真实睡眠，大量请求会耗时 → 用 `CyclicBarrier` 精确控制

---

## 7. 扩展思考

- **实际成本数据**：从真实 LLM API 返回的 `usage` 字段获取精确 Token 数
- **预算告警**：当日/月成本超阈值时自动提醒
- **成本分配**：按部门/项目/客户分摊成本
- **模型路由策略**：根据任务复杂度自动选择性价比最高的模型
- **长期趋势预测**：基于历史数据预测未来成本
