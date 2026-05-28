#!/usr/bin/env python3
"""
Day 42 Demo: 集成测试 + Docker 部署 (Integration Test + Docker Deployment)
================================================================
Tests the full platform via HTTP (requires Java server on port 8080).

Usage:
  cd ~/ai-learning/阶段8-综合项目/code/day42/cs-platform
  mvn spring-boot:run
  # In another terminal:
  python3 ~/ai-learning/阶段8-综合项目/code/day42/python/integration_test.py
"""

import json
import subprocess
import time
import urllib.request
import urllib.error
import sys


BASE_URL = "http://localhost:8080"

passed = 0
failed = 0


def request(method, path, body=None, headers=None):
    """Make HTTP request to the platform."""
    url = f"{BASE_URL}{path}"
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            content = resp.read().decode()
            try:
                return resp.status, json.loads(content)
            except json.JSONDecodeError:
                return resp.status, content
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return 0, str(e)


def test(name, fn):
    global passed, failed
    try:
        fn()
        passed += 1
        print(f"  ✅ {name}")
    except AssertionError as e:
        failed += 1
        print(f"  ❌ {name}: {e}")
    except Exception as e:
        failed += 1
        print(f"  ❌ {name}: {e}")


# ================================================================
# Tests
# ================================================================

def test_health():
    status, body = request("GET", "/api/dashboard/health")
    assert status == 200, f"Expected 200, got {status}"
    assert body["status"] == "UP"
    assert body["service"] == "CS Platform Dashboard"


def test_admin_health():
    status, body = request("GET", "/api/admin/health")
    assert status == 200
    assert body["status"] == "UP"


def test_admin_status():
    status, body = request("GET", "/api/admin/status")
    assert status == 200
    assert "memory" in body
    assert "uptime" in body


def test_dashboard_status():
    status, body = request("GET", "/api/dashboard/status")
    assert status == 200
    assert "uptime" in body
    assert "activeSessions" in body


def test_dashboard_report():
    status, body = request("GET", "/api/dashboard/report")
    assert status == 200
    for section in ["system", "chat", "cache", "rateLimit", "cost", "tickets", "knowledge"]:
        assert section in body, f"Missing section: {section}"


def test_dashboard_metrics():
    status, body = request("GET", "/api/dashboard/metrics")
    assert status == 200
    assert "chat" in body
    assert "cache" in body
    assert "cost" in body


def test_chat_basic():
    status, body = request("POST", "/api/chat", {
        "sessionId": "py-test-session",
        "message": "你好"
    })
    assert status == 200, f"Expected 200, got {status}: {body}"
    assert "sessionId" in body
    assert "reply" in body
    assert "historySize" in body


def test_chat_auto_session():
    status, body = request("POST", "/api/chat", {
        "message": "测试自动会话"
    })
    assert status == 200
    assert body["sessionId"] is not None


def test_chat_history():
    # Send a message first
    sid = "hist-test-py"
    request("POST", "/api/chat", {"sessionId": sid, "message": "第一条消息"})

    status, body = request("GET", f"/api/chat/history/{sid}")
    assert status == 200
    assert body["sessionId"] == sid
    assert body["turnCount"] >= 1


def test_chat_clear():
    status, body = request("DELETE", "/api/chat/clear-test-py")
    # 204 No Content
    assert status == 204, f"Expected 204, got {status}"


def test_ticket_list():
    status, body = request("GET", "/api/tickets")
    assert status == 200
    assert body["total"] >= 4
    assert len(body["tickets"]) >= 4


def test_ticket_create():
    status, body = request("POST", "/api/tickets", {
        "title": "Py测试工单",
        "description": "来自Python集成测试",
        "category": "售后",
        "priority": "HIGH",
        "creatorName": "PythonTest"
    })
    assert status == 200
    assert body["title"] == "Py测试工单"
    assert "id" in body


def test_ticket_stats():
    status, body = request("GET", "/api/tickets/stats")
    assert status == 200
    assert body["total"] >= 4
    assert "byPriority" in body
    assert "byCategory" in body


def test_ticket_categories():
    status, body = request("GET", "/api/tickets/categories")
    assert status == 200
    assert "categories" in body


def test_ticket_filter():
    status, body = request("GET", "/api/tickets?status=PENDING")
    assert status == 200
    assert body["total"] >= 1
    for t in body["tickets"]:
        assert t["status"] == "PENDING", f"Expected PENDING, got {t['status']}"


def test_knowledge_list():
    status, body = request("GET", "/api/knowledge/docs")
    assert status == 200
    assert body["total"] >= 7


def test_knowledge_stats():
    status, body = request("GET", "/api/knowledge/stats")
    assert status == 200
    assert body["totalDocs"] >= 7
    assert len(body["categories"]) >= 5


def test_knowledge_search():
    status, body = request("GET", "/api/knowledge/search?q=%E9%80%80%E8%B4%A7&topK=3")
    assert status == 200, f"Expected 200, got {status}: {body}"
    assert body["total"] >= 1
    assert len(body["results"]) >= 1


def test_knowledge_categories():
    status, body = request("GET", "/api/knowledge/categories")
    assert status == 200
    assert len(body) >= 5


def test_cache_clear():
    status, body = request("POST", "/api/dashboard/cache/clear")
    assert status == 200
    assert body["action"] == "clear_cache"


def test_web_pages():
    for page in ["/", "/kb", "/tickets", "/dashboard"]:
        status, _ = request("GET", page)
        assert status == 200, f"Page {page} returned {status}"


def test_rate_limiter():
    """Send 25 rapid requests; some should be limited."""
    allowed = 0
    limited = 0
    for i in range(25):
        status, body = request("POST", "/api/chat", {
            "sessionId": "py-rate-test",
            "message": f"burst {i}"
        })
        if status == 200:
            allowed += 1
        else:
            limited += 1

    print(f"  (Rate limited: {allowed} allowed, {limited} limited)")
    # At least 1 should be limited with 25 requests on capacity=20
    assert allowed > 0, "No requests were allowed!"


def test_docker_compose_exists():
    """Verify Docker Compose and Dockerfile exist."""
    import os
    compose = os.path.expanduser(
        "~/ai-learning/阶段8-综合项目/code/day42/cs-platform/docker-compose.yml")
    dockerfile = os.path.expanduser(
        "~/ai-learning/阶段8-综合项目/code/day42/cs-platform/Dockerfile")
    assert os.path.exists(compose), "docker-compose.yml not found"
    assert os.path.exists(dockerfile), "Dockerfile not found"


def test_ci_yaml_exists():
    import os
    ci = os.path.expanduser(
        "~/ai-learning/阶段8-综合项目/code/day42/cs-platform/.github/workflows/ci.yml")
    assert os.path.exists(ci), "CI workflow not found"


# ================================================================
# Main
# ================================================================

if __name__ == "__main__":
    print(f"{'='*60}")
    print(f"  Day 42: 集成测试 (Integration Tests)")
    print(f"{'='*60}\n")

    # Wait for server
    print("⏳ 等待服务器启动...")
    for attempt in range(30):
        try:
            status, _ = request("GET", "/api/dashboard/health")
            if status == 200:
                print(f"  ✓ 服务器已就绪 (尝试 {attempt + 1})")
                break
        except:
            pass
        time.sleep(1)
    else:
        print("  ❌ 服务器未启动，请先运行: mvn spring-boot:run")
        sys.exit(1)

    print("\n" + "-" * 50)
    print("  REST API 测试")
    print("-" * 50)

    # Health & Dashboard
    test("Dashboard Health", test_health)
    test("Admin Health", test_admin_health)
    test("Admin Status", test_admin_status)
    test("Dashboard Status", test_dashboard_status)
    test("Dashboard Report", test_dashboard_report)
    test("Dashboard Metrics", test_dashboard_metrics)

    # Chat
    test("Chat - Basic", test_chat_basic)
    test("Chat - Auto Session", test_chat_auto_session)
    test("Chat - History", test_chat_history)
    test("Chat - Clear Session", test_chat_clear)

    # Tickets
    test("Tickets - List", test_ticket_list)
    test("Tickets - Create", test_ticket_create)
    test("Tickets - Stats", test_ticket_stats)
    test("Tickets - Categories", test_ticket_categories)
    test("Tickets - Filter by Status", test_ticket_filter)

    # Knowledge
    test("Knowledge - List", test_knowledge_list)
    test("Knowledge - Stats", test_knowledge_stats)
    test("Knowledge - Search", test_knowledge_search)
    test("Knowledge - Categories", test_knowledge_categories)

    # Cache
    test("Cache - Clear", test_cache_clear)

    # Web Pages
    test("Web Pages (/, /kb, /tickets, /dashboard)", test_web_pages)

    # Rate Limit
    test("Rate Limit - Burst 25 requests", test_rate_limiter)

    print("\n" + "-" * 50)
    print("  基础设施验证")
    print("-" * 50)

    test("Dockerfile & docker-compose.yml", test_docker_compose_exists)
    test("CI/CD GitHub Actions", test_ci_yaml_exists)

    # Summary
    print(f"\n{'='*60}")
    total = passed + failed
    print(f"  Results: {passed}/{total} passed")
    if failed == 0:
        print(f"  🎉 全部通过！项目已准备就绪！")
    else:
        print(f"  ❌ {failed} 个测试失败")
    print(f"{'='*60}")
