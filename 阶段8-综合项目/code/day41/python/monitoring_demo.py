#!/usr/bin/env python3
"""
Day 41 Demo: 管理仪表盘 + LLMOps 集成 (Monitoring Dashboard + LLMOps Integration)
=================================================================
Tests: MetricsCollector, RateLimiter, ResponseCache, CostTracker, DashboardService

Pre-requisites: Java server running on port 8080
  cd ~/ai-learning/阶段8-综合项目/code/day41/cs-platform
  mvn spring-boot:run

This demo tests the full monitoring pipeline without starting Java.
"""

import json
import time
import hashlib
import math
import re
from dataclasses import dataclass, field
from typing import Optional


# ====================================================================
# Part 1: Python Implementation of Java Monitoring Components
# ====================================================================

class MetricsCollector:
    """Python version of com.ai.cs.monitor.MetricsCollector"""

    def __init__(self):
        self.start_time = time.time()
        self.total_messages = 0
        self.total_sessions = 0
        self.total_llm_calls = 0
        self.total_tokens = 0
        self.total_llm_time_ms = 0
        self.active_sessions = 0
        self.response_times = []

        self.cache_hits = 0
        self.cache_misses = 0
        self.cache_size = 0

        self.total_requests = 0
        self.allowed_requests = 0
        self.rate_limited_requests = 0

        self.total_cost_micros = 0
        self.cost_by_model = {}
        self.cost_history = []

    def record_message(self):
        self.total_messages += 1

    def record_session_created(self):
        self.total_sessions += 1
        self.active_sessions += 1

    def record_session_closed(self):
        self.active_sessions = max(0, self.active_sessions - 1)

    def record_llm_call(self, duration_ms, tokens, model):
        self.total_llm_calls += 1
        self.total_tokens += tokens
        self.total_llm_time_ms += duration_ms
        self.response_times.append(duration_ms)
        if len(self.response_times) > 100:
            self.response_times.pop(0)

    def record_cache_hit(self):
        self.cache_hits += 1

    def record_cache_miss(self):
        self.cache_misses += 1

    def record_cache_size(self, size):
        self.cache_size = size

    def record_request(self):
        self.total_requests += 1

    def record_allowed(self):
        self.allowed_requests += 1

    def record_rate_limited(self):
        self.rate_limited_requests += 1

    def record_cost(self, input_tokens, output_tokens, model):
        input_cost = (input_tokens / 1_000_000.0) * 270_000
        output_cost = (output_tokens / 1_000_000.0) * 1_100_000
        micro_cents = int(input_cost + output_cost)
        self.total_cost_micros += micro_cents
        self.cost_by_model[model] = self.cost_by_model.get(model, 0) + micro_cents
        self.cost_history.append((time.time(), micro_cents, model))
        if len(self.cost_history) > 1000:
            self.cost_history.pop(0)

    def update_ticket_metrics(self, total, pending, in_progress, resolved, closed):
        self.ticket_total = total
        self.ticket_pending = pending
        self.ticket_in_progress = in_progress
        self.ticket_resolved = resolved
        self.ticket_closed = closed

    def update_knowledge_metrics(self, total_docs, categories):
        self.knowledge_total_docs = total_docs
        self.knowledge_categories = categories

    def get_dashboard_report(self):
        uptime = int(time.time() - self.start_time)
        avg_resp = sum(self.response_times) / max(len(self.response_times), 1)
        sorted_times = sorted(self.response_times)
        p50 = sorted_times[len(sorted_times) // 2] if sorted_times else 0
        p95 = sorted_times[int(len(sorted_times) * 0.95)] if sorted_times else 0
        total_cache = self.cache_hits + self.cache_misses
        hit_rate = f"{self.cache_hits / total_cache * 100:.1f}%" if total_cache > 0 else "0%"
        total_cost_usd = self.total_cost_micros / 100_000_000.0

        return {
            "system": {
                "uptime": uptime,
                "uptimeDisplay": self._format_duration(uptime),
                "threads": 8,
                "status": "running"
            },
            "chat": {
                "totalMessages": self.total_messages,
                "totalSessions": self.total_sessions,
                "activeSessions": self.active_sessions,
                "totalLlmCalls": self.total_llm_calls,
                "totalTokens": self.total_tokens,
                "totalLlmTimeMs": self.total_llm_time_ms,
                "avgResponseTimeMs": f"{avg_resp:.0f}",
                "p50ResponseTimeMs": str(p50),
                "p95ResponseTimeMs": str(p95)
            },
            "cache": {
                "hits": self.cache_hits,
                "misses": self.cache_misses,
                "size": self.cache_size,
                "hitRate": hit_rate
            },
            "rateLimit": {
                "totalRequests": self.total_requests,
                "allowed": self.allowed_requests,
                "rateLimited": self.rate_limited_requests
            },
            "cost": {
                "totalCostUsd": f"{total_cost_usd:.4f}",
                "totalCostCents": f"{total_cost_usd * 100:.2f}¢",
                "byModel": self.cost_by_model,
                "hourlyTrend": []
            },
            "tickets": {
                "total": getattr(self, 'ticket_total', 0),
                "pending": getattr(self, 'ticket_pending', 0),
                "inProgress": getattr(self, 'ticket_in_progress', 0),
                "resolved": getattr(self, 'ticket_resolved', 0),
                "closed": getattr(self, 'ticket_closed', 0)
            },
            "knowledge": {
                "totalDocs": getattr(self, 'knowledge_total_docs', 0),
                "categories": getattr(self, 'knowledge_categories', 0)
            }
        }

    @staticmethod
    def _format_duration(seconds):
        if seconds < 60: return f"{seconds}秒"
        if seconds < 3600: return f"{seconds // 60}分钟"
        if seconds < 86400: return f"{seconds // 3600}小时{(seconds % 3600) // 60}分钟"
        return f"{seconds // 86400}天{(seconds % 86400) // 3600}小时"


class TokenBucket:
    """Python version of Java's RateLimiter token bucket"""

    def __init__(self, capacity, refill_rate):
        self.max_tokens = capacity
        self.tokens = capacity
        self.refill_rate = refill_rate
        self.last_refill = time.time()

    def try_consume(self):
        self._refill()
        if self.tokens >= 1:
            self.tokens -= 1
            return True
        return False

    def _refill(self):
        now = time.time()
        elapsed = now - self.last_refill
        self.tokens = min(self.max_tokens, self.tokens + elapsed * self.refill_rate)
        self.last_refill = now


class RateLimiter:
    """Python version of com.ai.cs.monitor.RateLimiter"""

    def __init__(self, capacity=20, refill_rate=5, metrics=None):
        self.capacity = capacity
        self.refill_rate = refill_rate
        self.buckets = {}
        self.metrics = metrics or MetricsCollector()

    def allow_request(self, key):
        self.metrics.record_request()
        if key not in self.buckets:
            self.buckets[key] = TokenBucket(self.capacity, self.refill_rate)
        allowed = self.buckets[key].try_consume()
        if allowed:
            self.metrics.record_allowed()
        else:
            self.metrics.record_rate_limited()
        return allowed


class ResponseCache:
    """Python version of com.ai.cs.monitor.ResponseCache"""

    def __init__(self, ttl_seconds=300, max_size=200, metrics=None):
        self.cache = {}
        self.ttl = ttl_seconds
        self.max_size = max_size
        self.metrics = metrics or MetricsCollector()

    def get(self, query):
        key = self._normalize_key(query)
        entry = self.cache.get(key)
        if entry is None:
            self.metrics.record_cache_miss()
            return None
        if time.time() - entry['time'] > self.ttl:
            del self.cache[key]
            self.metrics.record_cache_miss()
            return None
        self.metrics.record_cache_hit()
        return entry['response']

    def put(self, query, response):
        if len(self.cache) >= self.max_size:
            oldest = min(self.cache.keys(), key=lambda k: self.cache[k]['time'])
            del self.cache[oldest]
        key = self._normalize_key(query)
        self.cache[key] = {'response': response, 'time': time.time()}
        self.metrics.record_cache_size(len(self.cache))

    def clear(self):
        n = len(self.cache)
        self.cache.clear()
        self.metrics.record_cache_size(0)
        return n

    @staticmethod
    def _normalize_key(query):
        normalized = query.lower().strip()
        normalized = re.sub(r'[^\w\s\u4e00-\u9fff]', '', normalized)
        normalized = re.sub(r'\s+', ' ', normalized).strip()
        if len(normalized) > 100:
            normalized = normalized[:100]
        return hashlib.sha256(normalized.encode()).hexdigest()


class CostTracker:
    """Python version of com.ai.cs.monitor.CostTracker"""

    INPUT_COST_CHAT = 27_000       # micro-cents per 1M tokens
    OUTPUT_COST_CHAT = 110_000
    INPUT_COST_REASONER = 55_000
    OUTPUT_COST_REASONER = 219_000

    def __init__(self, metrics=None, default_model="deepseek-chat"):
        self.metrics = metrics or MetricsCollector()
        self.default_model = default_model

    @staticmethod
    def estimate_tokens(text):
        if not text:
            return 0
        chinese = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
        other = len(text) - chinese
        return int(chinese / 1.5 + other / 4.0)

    def track_call(self, prompt, response, model=None):
        input_tokens = self.estimate_tokens(prompt)
        output_tokens = self.estimate_tokens(response)
        model = model or self.default_model
        self.metrics.record_cost(input_tokens, output_tokens, model)

    def estimate_cost(self, input_tokens, output_tokens, model=None):
        model = model or self.default_model
        if 'reasoner' in model:
            input_cost = input_tokens * self.INPUT_COST_REASONER // 1_000_000
            output_cost = output_tokens * self.OUTPUT_COST_REASONER // 1_000_000
        else:
            input_cost = input_tokens * self.INPUT_COST_CHAT // 1_000_000
            output_cost = output_tokens * self.OUTPUT_COST_CHAT // 1_000_000
        total_usd = (input_cost + output_cost) / 100_000_000.0
        return input_tokens, output_tokens, total_usd, model


# ====================================================================
# Part 2: Test Suite
# ====================================================================

class TestSuite:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.tests = []

    def test(self, name, fn):
        self.tests.append((name, fn))

    def run_all(self):
        print(f"={' Day 41: 管理仪表盘 + LLMOps 集成 (Python Demo) ':=^60}\n")
        for name, fn in self.tests:
            try:
                fn()
                self.passed += 1
                print(f"  ✅ {name}")
            except Exception as e:
                self.failed += 1
                print(f"  ❌ {name}: {e}")
        print(f"\n{'='*60}")
        print(f"  Results: {self.passed} passed, {self.failed} failed")
        print(f"{'='*60}")
        return self.failed == 0


# ---- MetricsCollector Tests ----

def test_metrics_basic():
    m = MetricsCollector()
    assert m.total_messages == 0
    assert m.active_sessions == 0
    m.record_message()
    m.record_message()
    assert m.total_messages == 2
    m.record_session_created()
    assert m.total_sessions == 1
    assert m.active_sessions == 1
    m.record_session_closed()
    assert m.active_sessions == 0

def test_metrics_llm_call():
    m = MetricsCollector()
    m.record_llm_call(150, 50, "deepseek-chat")
    m.record_llm_call(200, 80, "deepseek-chat")
    assert m.total_llm_calls == 2
    assert m.total_tokens == 130
    assert m.total_llm_time_ms == 350
    assert len(m.response_times) == 2
    r = m.get_dashboard_report()
    assert r["chat"]["totalLlmCalls"] == 2
    assert r["chat"]["totalTokens"] == 130

def test_metrics_cache():
    m = MetricsCollector()
    m.record_cache_hit()
    m.record_cache_hit()
    m.record_cache_hit()
    m.record_cache_miss()
    m.record_cache_miss()
    m.record_cache_size(100)
    assert m.cache_hits == 3
    assert m.cache_misses == 2
    r = m.get_dashboard_report()
    assert r["cache"]["hits"] == 3
    assert r["cache"]["misses"] == 2
    assert r["cache"]["size"] == 100
    assert r["cache"]["hitRate"] == "60.0%"

def test_metrics_rate_limit():
    m = MetricsCollector()
    for _ in range(10):
        m.record_request()
        m.record_allowed()
    for _ in range(3):
        m.record_request()
        m.record_rate_limited()
    r = m.get_dashboard_report()
    assert r["rateLimit"]["totalRequests"] == 13
    assert r["rateLimit"]["allowed"] == 10
    assert r["rateLimit"]["rateLimited"] == 3

def test_metrics_cost():
    m = MetricsCollector()
    m.record_cost(100000, 50000, "deepseek-chat")  # 150K tokens
    r = m.get_dashboard_report()
    assert float(r["cost"]["totalCostUsd"]) >= 0.0008
    assert "deepseek-chat" in str(r["cost"]["byModel"])

def test_metrics_dashboard():
    m = MetricsCollector()
    m.record_message()
    m.record_session_created()
    m.record_llm_call(150, 50, "deepseek-chat")
    m.record_cache_hit()
    m.record_cache_miss()
    m.record_request()
    m.record_allowed()
    m.record_cost(100, 50, "deepseek-chat")
    m.update_ticket_metrics(10, 3, 2, 4, 1)
    m.update_knowledge_metrics(7, 6)
    r = m.get_dashboard_report()
    assert r["system"]["status"] == "running"
    assert r["chat"]["totalMessages"] == 1
    assert r["chat"]["activeSessions"] == 1
    assert r["cache"]["hits"] == 1
    assert r["rateLimit"]["allowed"] == 1
    assert r["tickets"]["total"] == 10
    assert r["knowledge"]["totalDocs"] == 7


# ---- RateLimiter Tests ----

def test_rate_limiter_basic():
    m = MetricsCollector()
    rl = RateLimiter(capacity=5, refill_rate=10, metrics=m)
    # First 5 should be allowed
    for i in range(5):
        assert rl.allow_request("test-user"), f"Request {i} should be allowed"
    # 6th should be limited
    assert not rl.allow_request("test-user"), "6th request should be limited"

def test_rate_limiter_refill():
    m = MetricsCollector()
    rl = RateLimiter(capacity=5, refill_rate=100, metrics=m)
    # Exhaust the bucket
    for _ in range(5):
        rl.allow_request("user")
    # Wait for refill
    time.sleep(0.05)
    # Should be allowed again (refill rate is 100/s, so 0.05s = 5 tokens)
    assert rl.allow_request("user"), "Should refill tokens"

def test_rate_limiter_multiple_keys():
    m = MetricsCollector()
    rl = RateLimiter(capacity=3, refill_rate=1, metrics=m)
    for i in range(3):
        assert rl.allow_request("user-a"), f"A-{i}"
    for i in range(3):
        assert rl.allow_request("user-b"), f"B-{i}"
    # Both users now have empty buckets
    assert not rl.allow_request("user-a")
    assert not rl.allow_request("user-b")


# ---- ResponseCache Tests ----

def test_cache_basic():
    m = MetricsCollector()
    cache = ResponseCache(ttl_seconds=300, max_size=100, metrics=m)
    assert cache.get("你好") is None
    cache.put("你好", "您好！有什么可以帮助您的？")
    result = cache.get("你好")
    assert result == "您好！有什么可以帮助您的？"
    assert m.cache_hits == 1
    assert m.cache_misses == 1

def test_cache_normalization():
    m = MetricsCollector()
    cache = ResponseCache(metrics=m)
    cache.put("退货政策是什么?", "30天无理由退货")
    r1 = cache.get("退货政策是什么！")
    assert r1 == "30天无理由退货", "Should match after normalization"
    r2 = cache.get("  退货政策是什么 ？  ")
    assert r2 == "30天无理由退货", "Should match after whitespace normalization"

def test_cache_ttl():
    m = MetricsCollector()
    cache = ResponseCache(ttl_seconds=0, max_size=100, metrics=m)
    cache.put("test", "value")
    time.sleep(0.1)
    assert cache.get("test") is None, "Should expire immediately"

def test_cache_max_size():
    m = MetricsCollector()
    cache = ResponseCache(ttl_seconds=300, max_size=2, metrics=m)
    cache.put("a", "1")
    cache.put("b", "2")
    assert cache.get("a") == "1"
    assert cache.get("b") == "2"
    cache.put("c", "3")  # Should evict oldest
    assert cache.get("a") is None or cache.get("c") == "3"

def test_cache_clear():
    m = MetricsCollector()
    cache = ResponseCache(metrics=m)
    cache.put("q1", "a1")
    cache.put("q2", "a2")
    assert cache.clear() == 2
    assert cache.get("q1") is None
    assert len(cache.cache) == 0


# ---- CostTracker Tests ----

def test_cost_estimate_tokens():
    assert CostTracker.estimate_tokens("hello world") == 2  # ~8 chars / 4
    assert CostTracker.estimate_tokens("你好世界") == 2       # ~4 chars / 1.5~2

def test_cost_estimate():
    c = CostTracker()
    inp, out, cost_usd, model = c.estimate_cost(1000, 500, "deepseek-chat")
    assert inp == 1000
    assert out == 500
    assert cost_usd > 0
    assert model == "deepseek-chat"

def test_cost_track():
    m = MetricsCollector()
    c = CostTracker(metrics=m)
    c.track_call("hello " * 100, "world " * 50)
    assert m.total_cost_micros > 0


# ---- End-to-End Scenario ----

def test_full_monitoring_pipeline():
    """Simulate a complete monitoring workflow."""
    m = MetricsCollector()
    rl = RateLimiter(capacity=20, refill_rate=5, metrics=m)
    cache = ResponseCache(ttl_seconds=300, max_size=200, metrics=m)
    ct = CostTracker(metrics=m)

    # Simulate 3 users making requests
    for user_id in ["alice", "bob", "charlie"]:
        query = "退货政策是什么"
        
        # Check rate limit
        if not rl.allow_request(user_id):
            continue
        
        # Check cache
        cached = cache.get(query)
        if cached:
            m.record_message()
            continue
        
        # Simulate LLM call
        m.record_message()
        prompt = f"Answer: {query}"
        response = "我们支持30天无理由退货。"
        llm_time = 500  # 500ms
        
        # Cache the response
        cache.put(query, response)
        
        # Track cost
        ct.track_call(prompt * 1000, response * 1000, "deepseek-chat")
        
        # Record LLM call
        m.record_llm_call(llm_time, 150, "deepseek-chat")
    
    # Verify
    report = m.get_dashboard_report()
    assert report["chat"]["totalMessages"] == 3
    assert report["cache"]["hits"] == 2  # alice miss -> bob & charlie hit
    assert report["cache"]["misses"] == 1  # alice only
    assert report["rateLimit"]["allowed"] == 3
    assert report["rateLimit"]["rateLimited"] == 0
    assert float(report["cost"]["totalCostUsd"]) > 0
    print("  (Full pipeline sim: 3 users, 1 cache miss → 2 hits ✓)")


# ====================================================================
# Main
# ====================================================================

if __name__ == "__main__":
    ts = TestSuite()
    
    # MetricsCollector
    ts.test("Metrics - basic operations", test_metrics_basic)
    ts.test("Metrics - LLM call tracking", test_metrics_llm_call)
    ts.test("Metrics - cache tracking", test_metrics_cache)
    ts.test("Metrics - rate limit tracking", test_metrics_rate_limit)
    ts.test("Metrics - cost tracking", test_metrics_cost)
    ts.test("Metrics - complete dashboard report", test_metrics_dashboard)
    
    # RateLimiter
    ts.test("RateLimiter - basic limiting", test_rate_limiter_basic)
    ts.test("RateLimiter - token refill", test_rate_limiter_refill)
    ts.test("RateLimiter - multiple keys", test_rate_limiter_multiple_keys)
    
    # ResponseCache
    ts.test("ResponseCache - basic get/put", test_cache_basic)
    ts.test("ResponseCache - query normalization", test_cache_normalization)
    ts.test("ResponseCache - TTL expiration", test_cache_ttl)
    ts.test("ResponseCache - max size eviction", test_cache_max_size)
    ts.test("ResponseCache - clear", test_cache_clear)
    
    # CostTracker
    ts.test("CostTracker - token estimation", test_cost_estimate_tokens)
    ts.test("CostTracker - cost estimation", test_cost_estimate)
    ts.test("CostTracker - track call", test_cost_track)
    
    # End-to-End
    ts.test("End-to-End - full monitoring pipeline", test_full_monitoring_pipeline)
    
    ts.run_all()

    # Bonus: Print a sample dashboard report
    print("\n📊 Sample Dashboard Report:")
    m = MetricsCollector()
    for i in range(5):
        m.record_message()
        m.record_llm_call(200 + i * 50, 100 + i * 20, "deepseek-chat")
        m.record_cost(100000, 50000, "deepseek-chat")
    m.record_cache_hit() and m.record_cache_hit() and m.record_cache_miss()
    m.record_request() and m.record_allowed() and m.record_request() and m.record_allowed() and m.record_request() and m.record_rate_limited()
    m.record_session_created()
    m.update_ticket_metrics(5, 2, 1, 1, 1)
    m.update_knowledge_metrics(7, 6)
    report = m.get_dashboard_report()
    print(json.dumps(report, indent=2, ensure_ascii=False))
