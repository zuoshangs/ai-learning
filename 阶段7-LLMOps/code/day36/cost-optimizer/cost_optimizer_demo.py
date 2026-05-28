"""
Day 36 — 性能调优 + 成本分析 (Python 对照版)

演示：
1. 模型定价表 (Model Cost Table)
2. 成本分析 (Cost Analysis)
3. 性能基准测试 (Performance Benchmark)
4. 优化建议 (Optimization Suggestions)
5. 成本趋势 (Cost Trend)
"""

import time
import random
import math
import json
from datetime import datetime, timedelta
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed


# ============================================================
# 1. 模型定价
# ============================================================

MODEL_PRICING = {
    "deepseek-chat":     {"provider": "DeepSeek",  "input_per_1k_usd": 0.00027, "output_per_1k_usd": 0.00110},
    "deepseek-v4-flash": {"provider": "DeepSeek",  "input_per_1k_usd": 0.00027, "output_per_1k_usd": 0.00110},
    "deepseek-reasoner": {"provider": "DeepSeek",  "input_per_1k_usd": 0.00055, "output_per_1k_usd": 0.00219},
    "gpt-4o":            {"provider": "OpenAI",    "input_per_1k_usd": 0.00500, "output_per_1k_usd": 0.01500},
    "gpt-4o-mini":       {"provider": "OpenAI",    "input_per_1k_usd": 0.00015, "output_per_1k_usd": 0.00060},
    "claude-3-haiku":    {"provider": "Anthropic", "input_per_1k_usd": 0.00025, "output_per_1k_usd": 0.00125},
    "claude-3-sonnet":   {"provider": "Anthropic", "input_per_1k_usd": 0.00300, "output_per_1k_usd": 0.01500},
    "qwen-turbo":        {"provider": "阿里云",     "input_per_1k_usd": 0.00080, "output_per_1k_usd": 0.00200},
}

CNY_RATE = 7.2  # 美元汇率


def calculate_cost(model: str, prompt_tokens: int, completion_tokens: int) -> dict:
    pricing = MODEL_PRICING.get(model, MODEL_PRICING["deepseek-v4-flash"])
    usd = (prompt_tokens / 1000) * pricing["input_per_1k_usd"] + \
          (completion_tokens / 1000) * pricing["output_per_1k_usd"]
    cny = usd * CNY_RATE
    return {
        "model": model, "provider": pricing["provider"],
        "promptTokens": prompt_tokens, "completionTokens": completion_tokens,
        "totalTokens": prompt_tokens + completion_tokens,
        "costUsd": round(usd, 6), "costCny": round(cny, 4),
    }


# ============================================================
# 2. 使用记录 & 成本分析
# ============================================================

class UsageTracker:
    """追踪 LLM 使用和成本"""

    def __init__(self):
        self.records = []
        self._id = 0

    def record(self, user_id: str, model: str, prompt_tokens: int,
               completion_tokens: int, latency_ms: float, cached: bool = False):
        cost = calculate_cost(model, prompt_tokens, completion_tokens)
        self._id += 1
        r = {
            "id": f"req-{self._id}", "userId": user_id, "model": model,
            "promptTokens": prompt_tokens, "completionTokens": completion_tokens,
            "totalTokens": cost["totalTokens"],
            "costUsd": cost["costUsd"], "costCny": cost["costCny"],
            "latencyMs": latency_ms, "cached": cached,
            "timestamp": datetime.now().isoformat(),
        }
        self.records.append(r)
        return r

    def simulate(self, user_id: str = "user-test"):
        models = list(MODEL_PRICING.keys())
        model = random.choice(models)
        return self.record(
            user_id, model,
            random.randint(100, 2000),
            random.randint(50, 800),
            random.uniform(200, 3000),
            random.random() < 0.25,
        )

    def seed(self, count: int = 200):
        users = ["user-alice", "user-bob", "user-charlie"]
        for _ in range(count):
            self.simulate(random.choice(users))

    def cost_report(self, user_id: str = None, hours: int = 24) -> dict:
        since = datetime.now() - timedelta(hours=hours)
        filtered = [r for r in self.records
                    if datetime.fromisoformat(r["timestamp"]) > since
                    and (user_id is None or r["userId"] == user_id)]

        # 按模型汇总
        by_model = defaultdict(list)
        for r in filtered:
            by_model[r["model"]].append(r)

        model_breakdown = {}
        total_usd = total_cny = 0
        total_tokens = 0

        for model, recs in by_model.items():
            m_usd = sum(r["costUsd"] for r in recs)
            m_cny = sum(r["costCny"] for r in recs)
            m_tokens = sum(r["totalTokens"] for r in recs)
            model_breakdown[model] = {
                "requests": len(recs),
                "totalTokens": m_tokens,
                "costUsd": round(m_usd, 4),
                "costCny": round(m_cny, 2),
            }
            total_usd += m_usd
            total_cny += m_cny
            total_tokens += m_tokens

        return {
            "period": f"{hours}h",
            "userId": user_id or "all",
            "totalRequests": len(filtered),
            "totalTokens": total_tokens,
            "avgTokensPerRequest": round(total_tokens / len(filtered)) if filtered else 0,
            "totalCostUsd": round(total_usd, 4),
            "totalCostCny": round(total_cny, 2),
            "avgCostPerRequestUsd": round(total_usd / len(filtered), 6) if filtered else 0,
            "costByModel": model_breakdown,
        }

    def hourly_trend(self, hours: int = 24) -> list:
        now = datetime.now()
        trend = []
        for h in range(hours - 1, -1, -1):
            start = now - timedelta(hours=h + 1)
            end = now - timedelta(hours=h)
            in_window = [r for r in self.records
                         if start < datetime.fromisoformat(r["timestamp"]) <= end]
            trend.append({
                "label": "now" if h == 0 else f"-{h}h",
                "requests": len(in_window),
                "costUsd": round(sum(r["costUsd"] for r in in_window), 4),
                "costCny": round(sum(r["costCny"] for r in in_window), 2),
            })
        return trend


# ============================================================
# 3. 性能基准测试
# ============================================================

def run_benchmark(concurrency: int, total_requests: int,
                  simulated_latency_ms: int = 200) -> dict:
    """模拟并发性能基准测试"""
    print(f"  开始基准测试: concurrency={concurrency}, requests={total_requests}, "
          f"simLatency={simulated_latency_ms}ms")

    latencies = []
    errors = 0
    start = time.perf_counter()

    def single_request():
        req_start = time.perf_counter()
        time.sleep(simulated_latency_ms / 1000)
        req_end = time.perf_counter()
        return (req_end - req_start) * 1000

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(single_request) for _ in range(total_requests)]
        for f in as_completed(futures):
            try:
                lat = f.result()
                latencies.append(lat)
            except Exception:
                errors += 1

    elapsed_ms = (time.perf_counter() - start) * 1000
    latencies.sort()
    n = len(latencies)

    return {
        "concurrency": concurrency,
        "totalRequests": total_requests,
        "completedRequests": n,
        "errorCount": errors,
        "elapsedMs": round(elapsed_ms, 1),
        "simulatedLatencyMs": simulated_latency_ms,
        "p50LatencyMs": round(latencies[n // 2], 1) if n > 0 else 0,
        "p95LatencyMs": round(latencies[int(n * 0.95)], 1) if n > 0 else 0,
        "p99LatencyMs": round(latencies[int(n * 0.99)], 1) if n > 0 else 0,
        "avgLatencyMs": round(sum(latencies) / n, 1) if n > 0 else 0,
        "throughputRps": round(n / (elapsed_ms / 1000)) if elapsed_ms > 0 else 0,
    }


# ============================================================
# 4. 优化建议
# ============================================================

def generate_suggestions(tracker: UsageTracker) -> list:
    suggestions = []

    by_model = defaultdict(list)
    for r in tracker.records:
        by_model[r["model"]].append(r)

    for model, recs in by_model.items():
        cached = sum(1 for r in recs if r["cached"])
        total = len(recs)
        if total > 5:
            cache_rate = cached / total
            if cache_rate < 0.2:
                suggestions.append({
                    "type": "cache",
                    "severity": "high",
                    "model": model,
                    "currentCacheRate": f"{cache_rate * 100:.0f}%",
                    "suggestion": f"启用语义缓存，可节省约 {(1 - cache_rate) * 100:.0f}% 的重复调用费用"
                })

        avg_cost = sum(r["costUsd"] for r in recs) / total
        if avg_cost > 0.003 and model in ("deepseek-reasoner", "gpt-4o"):
            alt = "deepseek-v4-flash" if "deepseek" in model else "gpt-4o-mini"
            alt_cost = MODEL_PRICING[alt]["input_per_1k_usd"] * 0.5 + \
                       MODEL_PRICING[alt]["output_per_1k_usd"] * 0.2
            saving = avg_cost - alt_cost
            suggestions.append({
                "type": "model_switch",
                "severity": "medium",
                "model": model,
                "alternative": alt,
                "avgCostPerRequest": round(avg_cost, 6),
                "estimatedSavingPerRequest": round(saving, 6),
                "suggestion": f"考虑切换到 {alt}，每请求可节省 ${saving:.4f}"
            })

    return suggestions


# ============================================================
# 5. 演示
# ============================================================

def demo_pricing():
    print("=" * 60)
    print("💰 模型定价表")
    print("=" * 60)
    print(f"{'模型':<22} {'提供商':<10} {'输入 $/1K':<10} {'输出 $/1K':<10} {'一次调用(500/200)':<15}")
    print("-" * 70)
    for model, info in MODEL_PRICING.items():
        cost = calculate_cost(model, 500, 200)
        print(f"{model:<22} {info['provider']:<10} "
              f"${info['input_per_1k_usd']:<8.5f} ${info['output_per_1k_usd']:<8.5f} "
              f"${cost['costUsd']:<12.6f}")


def demo_cost_analysis():
    print("\n" + "=" * 60)
    print("📊 成本分析")
    print("=" * 60)

    tracker = UsageTracker()
    tracker.seed(200)

    print("\n全局成本报告 (24h):")
    report = tracker.cost_report()
    print(f"  总请求: {report['totalRequests']}")
    print(f"  总 Token: {report['totalTokens']:,}")
    print(f"  总费用: ${report['totalCostUsd']:.4f} (¥{report['totalCostCny']:.2f})")
    print(f"  平均每请求: ${report['avgCostPerRequestUsd']:.6f}")
    print(f"\n  按模型:")
    for model, info in report['costByModel'].items():
        print(f"    {model:<22} {info['requests']:>4}次  ${info['costUsd']:<8.4f} ¥{info['costCny']:<6.2f}")

    print(f"\n按用户筛选 (user-alice):")
    alice_report = tracker.cost_report("user-alice")
    print(f"  请求: {alice_report['totalRequests']} 费用: ${alice_report['totalCostUsd']:.4f}")


def demo_benchmark():
    print("\n" + "=" * 60)
    print("⚡ 性能基准测试")
    print("=" * 60)

    scenarios = [
        (1, 10, 200),    # 单线程
        (5, 25, 400),    # 5 并发，慢响应
        (10, 50, 200),   # 10 并发
        (20, 100, 100),  # 20 并发，快响应
    ]

    print(f"\n{'并发':>6} {'总请求':>8} {'模拟延迟':>10} {'完成':>6} {'耗时':>8} {'p50':>6} {'p95':>6} {'吞吐':>8}")
    print("-" * 65)
    for c, total, lat in scenarios:
        result = run_benchmark(c, total, lat)
        print(f"{result['concurrency']:>6} {result['totalRequests']:>8} "
              f"{result['simulatedLatencyMs']:>6}ms"
              f"{result['completedRequests']:>6} "
              f"{result['elapsedMs']:>7.0f}ms"
              f"{result['p50LatencyMs']:>6.0f}ms"
              f"{result['p95LatencyMs']:>6.0f}ms"
              f"{result['throughputRps']:>7}rps")


def demo_suggestions():
    print("\n" + "=" * 60)
    print("💡 优化建议")
    print("=" * 60)

    tracker = UsageTracker()
    # Simulate patterns that trigger suggestions
    for _ in range(50):
        tracker.record("user-test", "deepseek-reasoner", 1500, 600, 2500, False)
    for _ in range(30):
        tracker.record("user-test", "gpt-4o", 2000, 800, 3000, False)

    suggestions = generate_suggestions(tracker)

    print(f"\n共 {len(suggestions)} 条优化建议:")
    for s in suggestions:
        sev = {"high": "🔴", "medium": "🟡", "low": "🟢"}.get(s["severity"], "ℹ️")
        print(f"\n  {sev} [{s['type']}] {s.get('model', '')}")
        print(f"    {s['suggestion']}")
        if "currentCacheRate" in s:
            print(f"    当前缓存率: {s['currentCacheRate']}")
        if "estimatedSavingPerRequest" in s:
            print(f"    预计节省: ${s['estimatedSavingPerRequest']:.4f}/请求")


def demo_trend():
    print("\n" + "=" * 60)
    print("📈 成本趋势")
    print("=" * 60)

    tracker = UsageTracker()
    # Simulate hourly data
    for h in range(24):
        count = random.randint(1, 15)
        for _ in range(count):
            rec = tracker.simulate(f"user-hour{h}")
            # 伪造时间戳
            rec["timestamp"] = (datetime.now() - timedelta(hours=h)).isoformat()

    trend = tracker.hourly_trend(24)
    print("\n每小时成本趋势 (最近 12h):")
    for t in trend[:12]:
        bar = "█" * min(t["requests"], 15)
        print(f"  {t['label']:>5}: {bar:<15} {t['requests']:>2}次 ${t['costUsd']:.4f}")


if __name__ == "__main__":
    print("📊 LLM 性能调优 + 成本分析")
    print("=" * 60)

    demo_pricing()
    demo_cost_analysis()
    demo_benchmark()
    demo_suggestions()
    demo_trend()
