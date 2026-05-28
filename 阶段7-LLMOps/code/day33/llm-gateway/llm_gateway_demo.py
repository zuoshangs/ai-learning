"""
Day 33 — LLM 网关 + 限流 (Python 对照版)

演示：
1. 令牌桶限流算法
2. 滑动窗口限流算法
3. 熔断器 (Circuit Breaker)
4. API Key 鉴权
5. HTTP 网关代理
"""

import time
import threading
import json
import requests
from datetime import datetime, timedelta
from collections import deque


# ============================================================
# 1. 令牌桶限流器
# ============================================================
class TokenBucket:
    """令牌桶限流器"""

    def __init__(self, rate_per_minute: int):
        self.capacity = max(rate_per_minute // 6, 1)  # 最大突发 ~10 秒量
        self.refill_rate = rate_per_minute / 60.0  # 每秒补充令牌数
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

    def _refill(self):
        now = time.time()
        elapsed = now - self.last_refill
        self.tokens = min(self.capacity, self.tokens + elapsed * self.refill_rate)
        self.last_refill = now

    @property
    def current_tokens(self) -> int:
        with self._lock:
            self._refill()
            return int(self.tokens)


class TokenBucketRateLimiter:
    """令牌桶限流管理器"""

    def __init__(self, default_rpm: int = 60):
        self.default_rpm = default_rpm
        self._buckets: dict[str, TokenBucket] = {}

    def try_acquire(self, key: str) -> bool:
        if key not in self._buckets:
            # 支持按 key 差异化配置
            rpm = TIER_LIMITS.get(key, self.default_rpm)
            self._buckets[key] = TokenBucket(rpm)
        return self._buckets[key].try_acquire()

    def get_count(self, key: str) -> int:
        bucket = self._buckets.get(key)
        return bucket.current_tokens if bucket else self.default_rpm

    def get_limit(self, key: str) -> int:
        return TIER_LIMITS.get(key, self.default_rpm)

    def reset(self, key: str):
        self._buckets.pop(key, None)


# ============================================================
# 2. 滑动窗口限流器
# ============================================================
class SlidingWindowRateLimiter:
    """滑动窗口日志限流器"""

    WINDOW_MS = 60_000  # 1 分钟窗口

    def __init__(self, default_rpm: int = 60):
        self.default_rpm = default_rpm
        self._windows: dict[str, deque] = {}

    def try_acquire(self, key: str) -> bool:
        limit = TIER_LIMITS.get(key, self.default_rpm)
        if key not in self._windows:
            self._windows[key] = deque()

        now = time.time() * 1000
        window_start = now - self.WINDOW_MS
        timestamps = self._windows[key]

        # 清理过期记录
        while timestamps and timestamps[0] < window_start:
            timestamps.popleft()

        if len(timestamps) < limit:
            timestamps.append(now)
            return True
        return False

    def get_count(self, key: str) -> int:
        timestamps = self._windows.get(key)
        if not timestamps:
            return 0
        now = time.time() * 1000
        window_start = now - self.WINDOW_MS
        return sum(1 for t in timestamps if t >= window_start)

    def get_limit(self, key: str) -> int:
        return TIER_LIMITS.get(key, self.default_rpm)

    def reset(self, key: str):
        self._windows.pop(key, None)


# ============================================================
# 3. 熔断器 (Circuit Breaker)
# ============================================================
class CircuitState:
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class CircuitBreaker:
    """熔断器"""

    def __init__(self, failure_threshold: int = 5, reset_timeout_ms: int = 30_000,
                 half_open_max: int = 3):
        self.failure_threshold = failure_threshold
        self.reset_timeout_ms = reset_timeout_ms
        self.half_open_max = half_open_max
        self.state = CircuitState.CLOSED
        self.failure_count = 0
        self.success_count = 0
        self.total_requests = 0
        self.rejected_requests = 0
        self.last_state_change = time.time()
        self._lock = threading.Lock()

    def allow_request(self) -> bool:
        with self._lock:
            self.total_requests += 1
            if self.state == CircuitState.CLOSED:
                return True
            elif self.state == CircuitState.OPEN:
                elapsed_ms = (time.time() - self.last_state_change) * 1000
                if elapsed_ms >= self.reset_timeout_ms:
                    self.state = CircuitState.HALF_OPEN
                    self.success_count = 0
                    self.last_state_change = time.time()
                    return True
                self.rejected_requests += 1
                return False
            else:  # HALF_OPEN
                if self.success_count < self.half_open_max:
                    return True
                self.rejected_requests += 1
                return False

    def on_success(self):
        with self._lock:
            if self.state == CircuitState.HALF_OPEN:
                self.success_count += 1
                if self.success_count >= self.half_open_max:
                    self.state = CircuitState.CLOSED
                    self.failure_count = 0
                    self.success_count = 0
                    self.last_state_change = time.time()
            elif self.state == CircuitState.CLOSED:
                self.failure_count = 0  # 成功重置失败计数

    def on_failure(self):
        with self._lock:
            if self.state == CircuitState.CLOSED:
                self.failure_count += 1
                if self.failure_count >= self.failure_threshold:
                    self.state = CircuitState.OPEN
                    self.last_state_change = time.time()
            elif self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.OPEN
                self.last_state_change = time.time()

    def reset(self):
        with self._lock:
            self.state = CircuitState.CLOSED
            self.failure_count = 0
            self.success_count = 0
            self.total_requests = 0
            self.rejected_requests = 0

    def get_stats(self) -> dict:
        return {
            "state": self.state,
            "failureCount": self.failure_count,
            "totalRequests": self.total_requests,
            "rejectedRequests": self.rejected_requests,
        }


# ============================================================
# 4. API Key 管理器
# ============================================================
TIER_LIMITS = {
    "sk-gold-tier": 1000,
    "sk-silver-tier": 200,
    "sk-free-tier": 30,
}

API_KEYS = {
    "sk-gold-tier": {"tier": "gold", "owner": "VIP User"},
    "sk-silver-tier": {"tier": "silver", "owner": "Standard User"},
    "sk-free-tier": {"tier": "free", "owner": "Free User"},
}


def validate_api_key(api_key: str) -> dict | None:
    info = API_KEYS.get(api_key)
    if info:
        return {"key": api_key, **info}
    return None


# ============================================================
# 5. 演示函数
# ============================================================

def demo_token_bucket():
    """演示令牌桶限流"""
    print("=" * 60)
    print("📦 令牌桶限流演示")
    print("=" * 60)

    limiter = TokenBucketRateLimiter(default_rpm=60)

    # Gold key — 1000 rpm
    key = "sk-gold-tier"
    print(f"\n🔑 Gold 等级 (1000 rpm) — 快速连续请求:")
    success = 0
    failed = 0
    for i in range(20):
        if limiter.try_acquire(key):
            success += 1
        else:
            failed += 1
        print(f"   请求 {i + 1:2d}: {'✅' if limiter.try_acquire(key) else '⛔'}"
              f"  剩余令牌: {limiter.get_count(key)}")
    print(f"   结果: ✅ {success} / ⛔ {failed}")

    # Free key — 30 rpm, 模拟突发超限
    free_key = "sk-free-tier"
    print(f"\n🔑 Free 等级 (30 rpm) — 突发 50 请求:")
    success = failed = 0
    for i in range(50):
        if limiter.try_acquire(free_key):
            success += 1
        else:
            failed += 1
    print(f"   结果: ✅ {success} / ⛔ {failed} (令牌耗尽)")


def demo_sliding_window():
    """演示滑动窗口限流"""
    print("\n" + "=" * 60)
    print("🪟 滑动窗口限流演示")
    print("=" * 60)

    limiter = SlidingWindowRateLimiter(default_rpm=60)

    key = "sk-silver-tier"
    print(f"\n🔑 Silver 等级 (200 rpm) — 连续请求:")
    success = failed = 0
    for i in range(250):
        if limiter.try_acquire(key):
            success += 1
        else:
            failed += 1
    print(f"   结果: ✅ {success} / ⛔ {failed} (超过 200 的被限)")

    print(f"   当前窗口计数: {limiter.get_count(key)} (应在 200 以内)")


def demo_circuit_breaker():
    """演示熔断器"""
    print("\n" + "=" * 60)
    print("🛡️ 熔断器演示")
    print("=" * 60)

    cb = CircuitBreaker(failure_threshold=3, reset_timeout_ms=2000, half_open_max=2)

    # 阶段1: 连续失败 → 熔断打开
    print("\n阶段1: 连续失败 3 次 → 熔断打开")
    loop_i = 0
    for loop_i in range(7):
        allowed = cb.allow_request()
        if allowed:
            if loop_i < 3:
                cb.on_failure()
        status = "🟢 通过" if allowed else "🔴 拒绝"
        print(f"   请求 {loop_i + 1}: {status} | 状态={cb.state} | "
              f"失败数={cb.failure_count}")

    # 阶段2: 等待超时 → HALF_OPEN
    print("\n阶段2: 等待 2 秒...")
    time.sleep(2.1)

    allowed = cb.allow_request()
    print(f"   请求 {loop_i + 2}: {'🟢 通过 (HALF_OPEN)' if allowed else '🔴 拒绝'}")
    if allowed:
        cb.on_success()
        print(f"   记录成功: success_count={cb.success_count}")

    allowed = cb.allow_request()
    if allowed:
        cb.on_success()
    print(f"   请求 {loop_i + 3}: {'🟢 通过' if allowed else '🔴 拒绝'} → {cb.state}")

    print(f"\n📊 熔断器统计: {json.dumps(cb.get_stats(), ensure_ascii=False, indent=4)}")


def demo_gateway_pipeline():
    """模拟完整网关流程"""
    print("\n" + "=" * 60)
    print("🔁 完整网关流程演示")
    print("=" * 60)

    rate_limiter = TokenBucketRateLimiter(default_rpm=60)
    circuit_breaker = CircuitBreaker(failure_threshold=3, reset_timeout_ms=5000)

    test_cases = [
        ("sk-gold-tier", "正常对话请求", True),    # 正常
        ("sk-invalid", "无效 API Key", KeyError),  # 无效 key
        ("sk-free-tier", "限流测试 (快速连发)", None),  # 限流
        ("sk-free-tier", "", None),
        ("sk-free-tier", "", None),
        ("sk-free-tier", "", None),
        ("sk-free-tier", "", None),
        ("sk-free-tier", "", None),
    ]

    for key, desc, expect in test_cases:
        # 1. Auth
        auth = validate_api_key(key)
        if not auth:
            print(f"  🔴 Auth 失败: {key} (无效 API Key)")
            continue

        # 2. Rate limit
        if not rate_limiter.try_acquire(key):
            print(f"  ⛔ Rate Limit 超过: {auth['tier']}/{key[:8]}...")
            continue

        # 3. Circuit breaker
        if not circuit_breaker.allow_request():
            print(f"  🔴 Circuit Open: 请求被熔断")
            continue

        # 4. LLM call (mock)
        if expect is None:
            # 模拟限流
            print(f"  ⛔ 请求被限流 (连续发送)")
            rate_limiter.reset(key)
            continue

        print(f"  🟢 [{auth['tier']}] {desc}: ✅ 对话成功 (mock)")
        circuit_breaker.on_success()

    print(f"\n📊 最终状态:")
    print(f"   熔断器: {circuit_breaker.state}")
    print(f"   请求数: {circuit_breaker.total_requests}")


def test_speed():
    """压力测试: 对比两种限流算法性能"""
    print("\n" + "=" * 60)
    print("⚡ 性能对比: 令牌桶 vs 滑动窗口")
    print("=" * 60)

    import time

    tb = TokenBucketRateLimiter(1000)
    sw = SlidingWindowRateLimiter(1000)

    key = "perf-test"

    # 令牌桶
    start = time.perf_counter()
    for _ in range(500):
        tb.try_acquire(key)
    tb_time = time.perf_counter() - start

    # 滑动窗口
    start = time.perf_counter()
    for _ in range(500):
        sw.try_acquire(key)
    sw_time = time.perf_counter() - start

    print(f"\n   令牌桶 500 次: {tb_time * 1000:.2f}ms ({tb_time / 500 * 1e6:.0f}µs/次)")
    print(f"   滑动窗口 500 次: {sw_time * 1000:.2f}ms ({sw_time / 500 * 1e6:.0f}µs/次)")
    print(f"   差异: {'令牌桶' if tb_time < sw_time else '滑动窗口'} 快 "
          f"{abs(tb_time - sw_time) / max(tb_time, sw_time) * 100:.1f}%")


if __name__ == "__main__":
    print("🔐 LLM 网关 + 限流系统演示")
    print("=" * 60)

    demo_token_bucket()
    demo_sliding_window()
    demo_circuit_breaker()
    demo_gateway_pipeline()
    test_speed()
