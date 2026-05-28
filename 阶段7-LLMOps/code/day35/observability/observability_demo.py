"""
Day 35 — 可观测性 + 指标监控 (Python 对照版)

演示：
1. 自定义指标收集器 (Counters, Timers, Gauges)
2. 健康检查 (Health Indicator)
3. 请求链路追踪
4. Prometheus 指标格式
5. 仪表盘 API
"""

import time
import json
import random
import threading
from datetime import datetime, timedelta
from collections import defaultdict
from functools import wraps


# ============================================================
# 1. 指标收集器
# ============================================================

class MetricsCollector:
    """模拟 Micrometer 的指标收集器"""

    def __init__(self):
        self._lock = threading.Lock()

        # Counters (累计值)
        self.counters = defaultdict(int)

        # Timers (延迟分布)
        self.latencies = []  # 所有延迟样本
        self.last_latency = 0
        self.peak_latency = 0

        # Error breakdown
        self.error_types = defaultdict(int)

    def increment(self, name: str, value: int = 1):
        with self._lock:
            self.counters[name] += value

    def record_latency(self, ms: float):
        with self._lock:
            self.latencies.append(ms)
            self.last_latency = ms
            if ms > self.peak_latency:
                self.peak_latency = ms

    def record_error(self, error_type: str):
        with self._lock:
            self.counters["errors"] += 1
            self.error_types[error_type] += 1

    def get(self, name: str) -> int:
        return self.counters.get(name, 0)

    def get_snapshot(self) -> dict:
        with self._lock:
            total = self.counters.get("requests", 0)
            hits = self.counters.get("cache_hits", 0)
            misses = self.counters.get("cache_misses", 0)
            errors = self.counters.get("errors", 0)
            blocks = self.counters.get("rate_limit_blocks", 0)

            all_samples = self.latencies[-1000:]  # 最近 1000 个
            sorted_lat = sorted(all_samples)
            n = len(sorted_lat)

            return {
                "totalRequests": total,
                "totalErrors": errors,
                "cacheHits": hits,
                "cacheMisses": misses,
                "rateLimitBlocks": blocks,
                "hitRate": f"{hits / max(hits + misses, 1) * 100:.1f}%",
                "lastLatencyMs": self.last_latency,
                "peakLatencyMs": self.peak_latency,
                "p50LatencyMs": sorted_lat[n // 2] if n > 0 else 0,
                "p95LatencyMs": sorted_lat[int(n * 0.95)] if n > 0 else 0,
                "p99LatencyMs": sorted_lat[int(n * 0.99)] if n > 0 else 0,
                "errorBreakdown": dict(self.error_types),
            }

    def to_prometheus(self) -> str:
        """输出 Prometheus 格式的指标"""
        snap = self.get_snapshot()
        lines = [
            "# HELP llm_requests_total Total LLM requests",
            "# TYPE llm_requests_total counter",
            f"llm_requests_total {snap['totalRequests']}",
            "",
            "# HELP llm_errors_total Total LLM errors",
            "# TYPE llm_errors_total counter",
            f"llm_errors_total {snap['totalErrors']}",
            "",
            "# HELP llm_cache_hits_total Cache hit count",
            "# TYPE llm_cache_hits_total counter",
            f"llm_cache_hits_total {snap['cacheHits']}",
            "",
            "# HELP llm_latency_seconds LLM request latency",
            "# TYPE llm_latency_seconds summary",
            f'llm_latency_seconds{{quantile="0.5"}} {snap["p50LatencyMs"] / 1000}',
            f'llm_latency_seconds{{quantile="0.95"}} {snap["p95LatencyMs"] / 1000}',
            f'llm_latency_seconds{{quantile="0.99"}} {snap["p99LatencyMs"] / 1000}',
            f'llm_latency_seconds_count {len(self.latencies)}',
            f'llm_latency_seconds_sum {sum(self.latencies[-1000:]) / 1000}',
        ]
        return "\n".join(lines)

    def reset(self):
        with self._lock:
            self.counters.clear()
            self.latencies.clear()
            self.last_latency = 0
            self.peak_latency = 0
            self.error_types.clear()


# ============================================================
# 2. 请求追踪
# ============================================================

class Tracer:
    """简单请求追踪"""

    def __init__(self):
        self.traces = []

    def start_trace(self, name: str) -> str:
        trace_id = f"trace-{len(self.traces) + 1}-{int(time.time() * 1000000) % 1000000}"
        self.traces.append({
            "trace_id": trace_id,
            "name": name,
            "start": time.time(),
            "spans": [],
        })
        return trace_id

    def add_span(self, trace_id: str, span_name: str):
        for t in self.traces:
            if t["trace_id"] == trace_id:
                t["spans"].append({
                    "name": span_name,
                    "timestamp": time.time(),
                })

    def end_trace(self, trace_id: str, status: str = "ok"):
        for t in self.traces:
            if t["trace_id"] == trace_id:
                t["end"] = time.time()
                t["duration_ms"] = int((t["end"] - t["start"]) * 1000)
                t["status"] = status
                return t
        return None

    def get_traces(self, limit: int = 10) -> list:
        return sorted(self.traces, key=lambda x: x.get("end", 0), reverse=True)[:limit]


# ============================================================
# 3. 模拟 LLM 服务
# ============================================================

class SimulatedLlmService:
    """模拟带可观测性的 LLM 服务"""

    def __init__(self):
        self.metrics = MetricsCollector()
        self.tracer = Tracer()
        self.cache = {
            "hello": "Hello! How can I help you today?",
            "what is ai": "AI is the simulation of human intelligence by machines.",
            "how are you": "I'm doing great, thank you!",
        }
        self._lock = threading.Lock()

    def call(self, prompt: str) -> tuple[str, dict]:
        trace_id = self.tracer.start_trace(f"llm_call:{prompt[:20]}")

        with self._lock:
            self.metrics.increment("requests")

        self.tracer.add_span(trace_id, "auth")
        time.sleep(0.001)  # 模拟鉴权

        # 缓存命中检查
        normalized = prompt.strip().lower()
        self.tracer.add_span(trace_id, "cache_lookup")

        if normalized in self.cache:
            self.metrics.increment("cache_hits")
            latency = 2 + random.random() * 5  # 2-7ms
            self.metrics.record_latency(latency)
            self.tracer.add_span(trace_id, "cache_hit")
            self.tracer.end_trace(trace_id, "cache_hit")
            return self.cache[normalized], {"source": "cache", "trace_id": trace_id}

        # 模拟错误 (5%)
        if random.random() < 0.05:
            self.metrics.record_error("timeout")
            self.tracer.add_span(trace_id, "error")
            self.tracer.end_trace(trace_id, "error")
            raise RuntimeError("LLM timeout")

        # 模拟限流 (3%)
        if random.random() < 0.03:
            self.metrics.increment("rate_limit_blocks")
            self.tracer.end_trace(trace_id, "rate_limited")
            return "[RATE_LIMITED] Too many requests", {"source": "rate_limit", "trace_id": trace_id}

        # 正常 LLM 处理
        self.tracer.add_span(trace_id, "llm_processing")
        processing_time = 50 + random.random() * 450  # 50-500ms

        # 分阶段追踪
        self.tracer.add_span(trace_id, "tokenization")
        time.sleep(processing_time * 0.1)

        self.tracer.add_span(trace_id, "inference")
        time.sleep(processing_time * 0.7)

        self.tracer.add_span(trace_id, "post_processing")
        time.sleep(processing_time * 0.2)

        latency = processing_time + random.random() * 20
        self.metrics.record_latency(latency)
        self.metrics.increment("cache_misses")
        self.metrics.increment("tokens", random.randint(20, 200))

        response = f"关于「{prompt}」的回答：这是一个模拟的LLM响应。"
        self.tracer.end_trace(trace_id, "ok")
        return response, {"source": "llm", "trace_id": trace_id, "latency_ms": int(latency)}


# ============================================================
# 4. 演示
# ============================================================

def demo_metrics():
    """演示指标收集"""
    print("=" * 60)
    print("📊 指标收集演示")
    print("=" * 60)

    service = SimulatedLlmService()

    # 生成一些请求
    prompts = ["hello", "what is ai", "tell me a joke", "how are you", "hello again"]

    print("\n模拟 5 次请求:")
    for p in prompts:
        try:
            resp, meta = service.call(p)
            src = meta.get("source", "?")
            print(f"  {p:<20} → {src:<10} {resp[:30]}...")
        except Exception as e:
            print(f"  {p:<20} → ERROR: {e}")

    print(f"\n📈 指标快照:")
    snap = service.metrics.get_snapshot()
    for k, v in snap.items():
        if k != "errorBreakdown":
            print(f"  {k}: {v}")
    if snap["errorBreakdown"]:
        print(f"  errorBreakdown: {dict(snap['errorBreakdown'])}")


def demo_tracing():
    """演示请求追踪"""
    print("\n" + "=" * 60)
    print("🔍 请求链路追踪演示")
    print("=" * 60)

    service = SimulatedLlmService()

    print("\n发送请求并查看追踪:")
    try:
        resp, meta = service.call("what is machine learning")
        print(f"  响应: {resp[:40]}...")
        print(f"  Source: {meta['source']}")
        print(f"  Trace ID: {meta.get('trace_id', 'N/A')}")

        # 显示追踪详情
        traces = service.tracer.get_traces(1)
        if traces:
            t = traces[0]
            print(f"\n  追踪详情:")
            print(f"    ID: {t['trace_id']}")
            print(f"    Name: {t['name']}")
            print(f"    Duration: {t.get('duration_ms', '?')}ms")
            print(f"    Status: {t.get('status', '?')}")
            print(f"    Spans ({len(t['spans'])}):")
            for s in t['spans']:
                print(f"      └ {s['name']}")
    except Exception as e:
        print(f"  ERROR: {e}")


def demo_prometheus():
    """演示 Prometheus 格式"""
    print("\n" + "=" * 60)
    print("📋 Prometheus 指标格式")
    print("=" * 60)

    service = SimulatedLlmService()
    # 生成一些指标
    for _ in range(20):
        try:
            service.call(random.choice(["hello", "what is ai", "new query", "test"]))
        except:
            pass

    print("\nPrometheus 输出 (前 20 行):")
    prom = service.metrics.to_prometheus()
    for line in prom.split("\n")[:20]:
        print(f"  {line}")
    print(f"  ...")


def demo_health_check():
    """演示健康检查"""
    print("\n" + "=" * 60)
    print("🏥 健康检查演示")
    print("=" * 60)

    service = SimulatedLlmService()

    def health_check() -> dict:
        snap = service.metrics.get_snapshot()
        requests = snap["totalRequests"]
        errors = snap["totalErrors"]

        if requests < 5:
            status = "UP"
        elif errors / max(requests, 1) > 0.5:
            status = "DOWN"
        elif errors / max(requests, 1) > 0.2:
            status = "DEGRADED"
        else:
            status = "UP"

        return {
            "status": status,
            "uptime": "simulated",
            "metrics": {
                "totalRequests": requests,
                "totalErrors": errors,
                "hitRate": snap["hitRate"],
                "avgLatencyMs": snap["p50LatencyMs"],
            },
            "components": {
                "llm": {"status": "UP"},
                "cache": {"status": "UP", "hitRate": snap["hitRate"]},
                "metricsSystem": {"status": "UP", "type": "Micrometer compatible"},
            }
        }

    print(f"\n  初始健康状态:")
    print(f"  {json.dumps(health_check(), ensure_ascii=False, indent=4)}")

    # 模拟大量错误
    print("\n  模拟 8 次错误后...")
    for _ in range(8):
        service.metrics.record_error("timeout")

    print(f"  {json.dumps(health_check(), ensure_ascii=False, indent=4)}")


def demo_dashboard():
    """演示仪表盘"""
    print("\n" + "=" * 60)
    print("🖥️ 仪表盘 API 演示")
    print("=" * 60)

    service = SimulatedLlmService()

    # 生成多样化指标
    queries = ["hello"] * 5 + ["new query"] * 20 + ["what is ai"] * 3
    for q in queries:
        try:
            service.call(q)
        except:
            pass

    snap = service.metrics.get_snapshot()

    dashboard = {
        "summary": {
            "totalRequests": snap["totalRequests"],
            "totalErrors": snap["totalErrors"],
            "cacheHitRate": snap["hitRate"],
            "avgLatencyMs": snap["p50LatencyMs"],
        },
        "latency": {
            "last": snap["lastLatencyMs"],
            "peak": snap["peakLatencyMs"],
            "p50": snap["p50LatencyMs"],
            "p95": snap["p95LatencyMs"],
            "p99": snap["p99LatencyMs"],
        },
        "counters": {
            "requests": snap["totalRequests"],
            "tokens": snap.get("tokens", 0),
            "errors": snap["totalErrors"],
            "cacheHits": snap["cacheHits"],
            "cacheMisses": snap["cacheMisses"],
            "rateLimitBlocks": snap["rateLimitBlocks"],
        },
    }

    print(f"\n  仪表盘数据:")
    print(f"  {json.dumps(dashboard, ensure_ascii=False, indent=4)}")


if __name__ == "__main__":
    print("📡 可观测性系统演示")
    print("=" * 60)

    demo_metrics()
    demo_tracing()
    demo_prometheus()
    demo_health_check()
    demo_dashboard()
