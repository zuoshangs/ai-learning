"""
Day 29 — Dify 工作流平台 · Python 对照版

与 Java 版功能对应：
1. 部署 Dify（Docker Compose，不写代码）
2. 在 Dify Web UI 中创建 RAG 工作流（可视化拖拽）
3. Java 暴露工具 API → 注册为 Dify 自定义工具
4. Java 调用 Dify 工作流 API

本 Python 脚本演示：直接调用 Dify API + 模拟工具端点
"""

import requests
import json
import time
import argparse
from datetime import datetime


# ═══════════════════════════════════════════════════
# 第一部分：Dify API 调用
# ═══════════════════════════════════════════════════

class DifyClient:
    """Python 版 Dify 客户端 —— 对应 Java 的 DifyClient.java"""

    def __init__(self, base_url="http://localhost:5001", api_key=""):
        self.base_url = base_url.rstrip("/")
        self.headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }

    def run_workflow(self, query: str, user: str = "python-client"):
        """运行工作流（阻塞模式）"""
        url = f"{self.base_url}/v1/workflows/run"
        payload = {
            "inputs": {"query": query},
            "response_mode": "blocking",
            "user": user,
        }
        resp = requests.post(url, headers=self.headers, json=payload, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def send_message(self, query: str, conversation_id: str = ""):
        """发送聊天消息"""
        url = f"{self.base_url}/v1/chat-messages"
        payload = {
            "inputs": {},
            "query": query,
            "response_mode": "blocking",
            "conversation_id": conversation_id,
            "user": "python-client",
        }
        resp = requests.post(url, headers=self.headers, json=payload, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def list_apps(self):
        """获取应用列表"""
        url = f"{self.base_url}/v1/apps"
        resp = requests.get(url, headers=self.headers, timeout=10)
        resp.raise_for_status()
        return resp.json()

    def health_check(self):
        """健康检查"""
        url = f"{self.base_url}/health"
        resp = requests.get(url, timeout=5)
        return resp.status_code == 200


# ═══════════════════════════════════════════════════
# 第二部分：工具 API 模拟（对应 Java 的 ToolController）
# ═══════════════════════════════════════════════════

class JavaToolSimulator:
    """
    模拟 Java ToolController 的端点。
    实际部署时这些端点在 Java Spring Boot 上运行（localhost:8080）。
    这里模拟其行为，方便测试 Dify 的 HTTP 工具调用。
    """

    @staticmethod
    def get_weather(city: str) -> str:
        """查询天气 —— 对应 Java WeatherService"""
        try:
            resp = requests.get(
                f"https://wttr.in/{city}?format=%C+%t+%w+%h",
                timeout=10
            )
            return f"{city}天气: {resp.text.strip()}"
        except Exception as e:
            return f"天气查询失败: {e}"

    @staticmethod
    def calculate(expression: str) -> str:
        """数学计算 —— 对应 Java CalculatorService"""
        try:
            result = eval(expression, {"__builtins__": {}}, {})
            return f"{expression} = {result}"
        except Exception as e:
            return f"计算失败: {e}"

    @staticmethod
    def get_datetime() -> str:
        """日期时间 —— 对应 Java DateTimeService"""
        now = datetime.now()
        weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
        wd = weekdays[now.weekday()]
        return f"当前时间: {now.strftime('%Y年%m月%d日 %H:%M:%S')} {wd}"

    @staticmethod
    def handle_note(action: str, params: str = "") -> str:
        """笔记管理 —— 对应 Java MemoryNoteService"""
        notes = {}

        if action == "save":
            if ":" in params:
                key, val = params.split(":", 1)
                notes[key.strip()] = val.strip()
                return f"笔记已保存: {key.strip()}"
            return "请提供笔记内容（格式：标题:内容）"
        elif action == "read":
            val = notes.get(params.strip())
            return f"{params.strip()}: {val}" if val else f"未找到笔记: {params}"
        elif action == "list":
            return "\n".join(f"- {k}: {v}" for k, v in notes.items()) if notes else "暂无笔记"
        return f"未知操作: {action}（支持: save/read/list）"


# ═══════════════════════════════════════════════════
# 第三部分：OpenAPI 规范（对应 Java openApiSpec()）
# ═══════════════════════════════════════════════════

def generate_openapi_spec() -> dict:
    """生成 OpenAPI 3.0 规范 —— Dify 通过此规范自动发现工具"""
    return {
        "openapi": "3.0.0",
        "info": {"title": "Java Tools API", "version": "1.0.0"},
        "servers": [{"url": "http://localhost:8080"}],
        "paths": {
            "/api/tools/weather": {
                "post": {
                    "summary": "查询天气",
                    "description": "查询指定城市的天气情况",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string", "description": "城市名，如：北京"}
                                    }
                                }
                            }
                        }
                    },
                    "responses": {"200": {"description": "天气信息"}}
                }
            },
            "/api/tools/calculator": {
                "post": {
                    "summary": "数学计算",
                    "description": "执行数学表达式计算",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string", "description": "数学表达式"}
                                    }
                                }
                            }
                        }
                    },
                    "responses": {"200": {"description": "计算结果"}}
                }
            },
            "/api/tools/datetime": {
                "post": {
                    "summary": "日期时间",
                    "description": "获取当前日期和时间",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string", "description": "查询内容"}
                                    }
                                }
                            }
                        }
                    },
                    "responses": {"200": {"description": "时间信息"}}
                }
            },
            "/api/tools/note": {
                "post": {
                    "summary": "笔记管理",
                    "description": "保存或读取笔记",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "query": {"type": "string", "description": "操作：save/read/list"},
                                        "params": {"type": "string", "description": "笔记内容或关键词"}
                                    }
                                }
                            }
                        }
                    },
                    "responses": {"200": {"description": "操作结果"}}
                }
            }
        }
    }


# ═══════════════════════════════════════════════════
# 主测试逻辑
# ═══════════════════════════════════════════════════

def test_java_tools():
    """测试工具模拟器"""
    print("=" * 60)
    print("🧪 测试 Java 工具 API（模拟）")
    print("=" * 60)

    print(f"\n📌 天气查询:")
    print(f"  {JavaToolSimulator.get_weather('北京')}")

    print(f"\n📌 数学计算:")
    print(f"  {JavaToolSimulator.calculate('25 * 40')}")
    print(f"  {JavaToolSimulator.calculate('123 + 456 * 2')}")

    print(f"\n📌 日期时间:")
    print(f"  {JavaToolSimulator.get_datetime()}")

    print(f"\n📌 笔记管理:")
    print(f"  {JavaToolSimulator.handle_note('save', 'test:Hello Dify')}")
    print(f"  {JavaToolSimulator.handle_note('read', 'test')}")
    print(f"  {JavaToolSimulator.handle_note('list', '')}")

    print(f"\n✅ 工具模拟测试完成")


def test_dify_api():
    """测试 Dify API 连通性"""
    print("=" * 60)
    print("🧪 测试 Dify API 连通性")
    print("=" * 60)

    client = DifyClient()

    if client.health_check():
        print("\n✅ Dify 服务运行中")
        print(f"   API: http://localhost:5001")
    else:
        print("\n⚠️  Dify 未运行")
        print("   请先执行: cd day29/dify && docker compose up -d")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Dify 工作流平台 Python 对照版")
    parser.add_argument("--test", choices=["tools", "dify", "all"], default="all",
                        help="测试选项")
    args = parser.parse_args()

    if args.test in ("tools", "all"):
        test_java_tools()

    if args.test in ("dify", "all"):
        test_dify_api()
