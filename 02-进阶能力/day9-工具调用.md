# 第9天：工具调用（Function Calling）🛠️

> **学习目标：** 理解 Function Calling 的原理和工作流程，学会定义工具、解析工具调用、
>   执行函数、返回结果给模型，最终构建一个"智能助手"——能查天气、算数学、搜资料
> **预计时间：** 2.5小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4天（API 调用）、第8天（RAG 基础）

---

## 📋 目录

1. [为什么需要工具调用？](#1-为什么需要工具调用)
2. [Function Calling 核心原理](#2-function-calling-核心原理)
3. [工具一：天气查询](#3-工具一天气查询)
4. [工具二：计算器](#4-工具二计算器)
5. [工具三：信息搜索](#5-工具三信息搜索)
6. [实战：智能助手（多工具编排）](#6-实战智能助手下具编排)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么需要工具调用？

### 大模型的"天花板"

即使是最强的大模型，也有三件做不到的事：

| 做不到的事 | 举例 | 原因 |
|-----------|------|------|
| **实时信息** | "今天北京多少度？" | 模型知识截止于训练时间 |
| **精确计算** | "123456 × 789012 = ？" | 大模型不擅长精确数学 |
| **操作外部系统** | "帮我发一封邮件" | 模型没有操作系统的权限 |

### Function Calling 的解决方案

Function Calling（也叫 Tool Use）让大模型可以"请求调用工具"，开发者执行工具并返回结果。

```
用户：北京天气怎么样？
                │
                ▼
     ┌──────────────────────┐
     │      大模型           │
     │  "这个我需要查工具"    │
     │  返回: tool_call       │
     │  get_weather(city=北京)│
     └──────────┬───────────┘
                │
                ▼
     ┌──────────────────────┐
     │   开发者执行函数       │
     │  调用天气 API         │
     │  返回: "25°C, 晴"     │
     └──────────┬───────────┘
                │
                ▼
     ┌──────────────────────┐
     │      大模型           │
     │  基于工具结果生成回答  │
     │  "北京今天25°C，晴"   │
     └──────────────────────┘
```

**核心思想：** 模型决定**什么时候调用什么工具**，开发者负责**执行工具**。
模型不直接调用 API —— 它只是"请求"调用，真正的执行权在开发者手中。

### 传统方式 vs Function Calling

```python
# 🚫 传统方式：让模型生成 JSON，你猜它要干什么
prompt = "用户说：北京天气怎么样？请返回JSON格式的指令"
response = llm(prompt)   # {"action": "get_weather", "city": "北京"}
# 然后手动解析 JSON，不知道格式是否规范、参数是否正确

# ✅ Function Calling：模型用结构化方式请求工具调用
response = llm(
    messages=[{"role": "user", "content": "北京天气怎么样？"}],
    tools=TOOLS   # 预定义的工具列表
)
# 模型直接返回 tool_call: get_weather(city="北京")
# 格式是确定的、参数是符合 schema 的
```

---

## 2. Function Calling 核心原理

### 工作流程（四步循环）

```
第1步：用户提问
第2步：把问题 + 工具定义 发给模型
第3步：模型决定：
  ├─ 直接回答 → 返回给用户（结束）
  └─ 调用工具 → 返回 tool_calls
         │
         ▼
第4步：执行工具函数 → 把结果返回模型
         │
         └──→ 回到第2步（循环，直到模型直接回答）
```

### 工具定义（Tool Schema）

工具使用 **JSON Schema** 格式定义。这是模型的"说明书"——告诉模型有什么工具可用、什么时候用、参数是什么。

```json
{
  "type": "function",
  "function": {
    "name": "get_weather",           // 工具名称（模型用这个名字来调用）
    "description": "获取指定城市的天气信息",  // 描述（模型靠这个判断什么时候用）
    "parameters": {                   // 参数定义（JSON Schema 格式）
      "type": "object",
      "properties": {
        "city": {
          "type": "string",
          "description": "城市名称，如北京、上海"
        }
      },
      "required": ["city"]            // 必填参数
    }
  }
}
```

### 关键参数：tool_choice

| 参数值 | 行为 | 适用场景 |
|--------|------|---------|
| `"auto"`（默认） | 模型自主决定是否调工具 | 大多数场景 |
| `"required"` | 强制模型必须调一次工具 | 你想确保工具被调用 |
| `{"type":"function","function":{"name":"xxx"}}` | 强制调特定工具 | 指定用某个工具 |
| `"none"` | 禁止调工具 | 单纯问答 |

### API 交互格式

```python
# ─── 请求：传入工具定义 ───────────────────
request = {
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "北京的天气怎么样？"}],
    "tools": [TOOL_DEFINITIONS],    # 工具列表
    "tool_choice": "auto"
}

# ─── 响应：模型决定调工具 ────────────────
response = {
    "choices": [{
        "message": {
            "role": "assistant",
            "content": None,          # 没有文本回复
            "tool_calls": [{          # 模型请求调工具
                "id": "call_xxx",
                "type": "function",
                "function": {
                    "name": "get_weather",
                    "arguments": "{\"city\": \"北京\"}"
                }
            }]
        }
    }]
}

# ─── 第二次请求：传入工具结果 ─────────────
request = {
    "model": "deepseek-chat",
    "messages": [
        {"role": "user", "content": "北京的天气怎么样？"},
        {"role": "assistant", "content": None, "tool_calls": [...]},  # 模型请求
        {"role": "tool", "content": "{\"温度\": \"25\", \"天气\": \"晴\"}",  # 工具结果
         "tool_call_id": "call_xxx"}  # 对应 tool_calls 的 id
    ],
    "tools": [TOOL_DEFINITIONS]
}
```

### 消息数组的完整序列

```
messages = [
    # 第1轮：用户提问
    {"role": "user", "content": "北京的天气怎么样？"},
    
    # 第2条：模型请求调工具（由 API 返回）
    {"role": "assistant", "content": None, "tool_calls": [...]},
    
    # 第3条：开发者返回工具结果
    {"role": "tool", "content": "...", "tool_call_id": "call_xxx"},
    
    # 第4条：模型基于工具结果回答（API 再次返回）
    {"role": "assistant", "content": "北京今天25°C，晴天。"}
]
```

> **重要：** 每次 tool_call 都必须有对应的 tool 消息，ID 要匹配。
> 如果模型连续调用多个工具，可以一次性返回所有工具结果。

---

## 3. 工具一：天气查询

### 工具定义

```python
WEATHER_TOOL = {
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "获取指定城市的实时天气信息，包括温度、天气状况、湿度等",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {
                    "type": "string",
                    "description": "城市名称，如北京、上海、广州、深圳"
                },
                "date": {
                    "type": "string",
                    "description": "日期，格式为 YYYY-MM-DD，默认为今天"
                }
            },
            "required": ["city"]
        }
    }
}
```

### 执行函数（模拟数据）

> 由于真实天气 API 需要注册且有访问限制，本教程使用模拟数据。
> 实际项目中替换为真实 API 调用即可（如和风天气、OpenWeatherMap）。

```python
import random, datetime

def get_weather(city: str, date: str = None) -> dict:
    """获取城市天气（模拟数据）"""
    if date is None:
        date = datetime.date.today().strftime("%Y-%m-%d")
    
    # 模拟数据（实际项目中调用天气 API）
    weather_data = {
        "北京": {"temp": 25, "condition": "晴", "humidity": 45, "wind": "3级"},
        "上海": {"temp": 28, "condition": "多云", "humidity": 65, "wind": "4级"},
        "广州": {"temp": 32, "condition": "阵雨", "humidity": 80, "wind": "2级"},
        "深圳": {"temp": 31, "condition": "多云", "humidity": 75, "wind": "3级"},
        "杭州": {"temp": 27, "condition": "小雨", "humidity": 70, "wind": "3级"},
        "成都": {"temp": 26, "condition": "阴", "humidity": 60, "wind": "2级"},
        "武汉": {"temp": 30, "condition": "晴", "humidity": 55, "wind": "3级"},
    }
    
    base = weather_data.get(city, {"temp": 20, "condition": "未知", "humidity": 50, "wind": "2级"})
    
    # 加一点随机波动，模拟不同日期的变化
    seed = hash(f"{city}_{date}") % 100
    temp_offset = (seed % 7) - 3  # -3 到 +3
    humidity_offset = (seed % 15) - 7  # -7 到 +7
    
    return {
        "city": city,
        "date": date,
        "temperature": f"{base['temp'] + temp_offset}°C",
        "condition": base["condition"],
        "humidity": f"{min(100, max(0, base['humidity'] + humidity_offset))}%",
        "wind": base["wind"],
        "advice": get_advice(base["condition"])
    }

def get_advice(condition: str) -> str:
    advice = {
        "晴": "适合户外活动，注意防晒 ☀️",
        "多云": "天气不错，适宜出行 ⛅",
        "阴": "天气阴沉，建议带伞 🌥️",
        "小雨": "建议带伞，路面湿滑注意安全 🌦️",
        "阵雨": "可能有阵雨，随身带伞 🌧️",
    }
    return advice.get(condition, "天气多变，注意关注实时预报")
```

> **实际项目替换方案：**
> ```python
> import requests
> def get_weather_real(city, date=None):
>     key = "你的API_KEY"
>     url = f"https://api.seniverse.com/v3/weather/now.json?key={key}&location={city}"
>     resp = requests.get(url)
>     data = resp.json()
>     return {
>         "temperature": data["results"][0]["now"]["temperature"] + "°C",
>         "condition": data["results"][0]["now"]["text"],
>         ...
>     }
> ```

---

## 4. 工具二：计算器

计算器展示了一个"纯函数式"工具——不需要外部 API，本地就能精确执行。

### 工具定义

```python
CALCULATOR_TOOL = {
    "type": "function",
    "function": {
        "name": "calculator",
        "description": "执行精确的数学计算，适合大模型不擅长的复杂计算",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "数学表达式，如 '123456 * 789012'、'sqrt(144)'、'2**10'"
                }
            },
            "required": ["expression"]
        }
    }
}
```

### 执行函数

```python
import math

def calculator(expression: str) -> dict:
    """执行数学计算（安全环境）"""
    # 只允许安全的数学函数和运算符
    allowed_names = {
        k: v for k, v in math.__dict__.items()
        if not k.startswith("__")
    }
    allowed_names.update({
        "abs": abs, "round": round, "min": min, "max": max,
        "sum": sum, "pow": pow,
    })
    
    # 安全检查：禁止 __ 属性访问、import、exec、eval 等
    blacklist = ["__", "import", "exec", "eval", "open", "os.", "sys."]
    for word in blacklist:
        if word in expression:
            return {"error": f"表达式包含非法内容: {word}"}
    
    try:
        result = eval(expression, {"__builtins__": {}}, allowed_names)
        return {
            "expression": expression,
            "result": result,
            "formatted": f"{expression} = {result}"
        }
    except Exception as e:
        return {
            "expression": expression,
            "error": str(e)
        }
```

### 测试

```python
calculator("123456 * 789012")  
# → {"expression": "123456 * 789012", "result": 97399433472, "formatted": "123456 * 789012 = 97399433472"}

calculator("sqrt(144) + 2**10")  
# → {"expression": "sqrt(144) + 2**10", "result": 1036.0, ...}
```

---

## 5. 工具三：信息搜索

### 工具定义

```python
SEARCH_TOOL = {
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "搜索实时信息，适合查询最新新闻、知识截止日期后的信息",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词"
                },
                "num_results": {
                    "type": "integer",
                    "description": "返回结果数量，默认5条"
                }
            },
            "required": ["query"]
        }
    }
}
```

### 执行函数

```python
def web_search(query: str, num_results: int = 5) -> dict:
    """搜索网络信息（模拟数据）"""
    # 实际项目可接入 SerpAPI、Bing Search API 等
    # 这里演示数据结构
    database = {
        "杭州亚运会": {
            "results": [
                {"title": "杭州亚运会圆满闭幕", "snippet": "第19届亚洲运动会在杭州圆满落幕..."},
                {"title": "中国代表团创历史最佳", "snippet": "中国体育代表团在杭州亚运会上获得..."},
            ]
        },
        "AI最新进展": {
            "results": [
                {"title": "GPT-5 发布在即", "snippet": "OpenAI 宣布下一代大模型 GPT-5..."},
                {"title": "开源模型超越闭源", "snippet": "多个开源大模型在基准测试中..."},
            ]
        },
    }
    
    # 模糊匹配
    for key, data in database.items():
        if any(kw in query for kw in key):
            return data["results"][:num_results]
    
    return {"results": [], "note": "未找到相关信息，请尝试其他关键词"}
```

---

## 6. 实战：智能助手（多工具编排）

现在把三个工具组合在一起，构建一个真正的"智能助手"。

### 6.1 工具注册器

```python
"""
tool_registry.py — 工具注册和执行器
"""
import math, random, datetime

class ToolRegistry:
    """管理所有工具的注册和执行"""
    
    def __init__(self):
        self._tools = {}
        self._schemas = []
    
    def register(self, schema: dict, handler: callable):
        """注册一个工具"""
        name = schema["function"]["name"]
        self._tools[name] = handler
        self._schemas.append(schema)
    
    def get_schemas(self) -> list:
        """获取所有工具的 OpenAPI Schema"""
        return self._schemas
    
    def execute(self, tool_name: str, arguments: dict) -> dict:
        """执行一个工具"""
        if tool_name not in self._tools:
            return {"error": f"未知工具: {tool_name}"}
        handler = self._tools[tool_name]
        try:
            return handler(**arguments)
        except Exception as e:
            return {"error": f"工具执行失败: {str(e)}"}
    
    def list_tools(self) -> list:
        """列出所有工具名称"""
        return list(self._tools.keys())


# ─── 初始化注册器 ────────────────────────────
registry = ToolRegistry()

# ─── 天气工具 ────────────────────────────────
WEATHER_DATA = {
    "北京": {"temp": 25, "condition": "晴", "humidity": 45, "wind": "3级"},
    "上海": {"temp": 28, "condition": "多云", "humidity": 65, "wind": "4级"},
    "广州": {"temp": 32, "condition": "阵雨", "humidity": 80, "wind": "2级"},
    "深圳": {"temp": 31, "condition": "多云", "humidity": 75, "wind": "3级"},
    "杭州": {"temp": 27, "condition": "小雨", "humidity": 70, "wind": "3级"},
    "成都": {"temp": 26, "condition": "阴", "humidity": 60, "wind": "2级"},
}

ADVICE = {
    "晴": "适合户外活动，注意防晒 ☀️",
    "多云": "天气不错，适宜出行 ⛅",
    "阴": "天气阴沉，建议带伞 🌥️",
    "小雨": "建议带伞，路面湿滑 🌦️",
    "阵雨": "可能下雨，随身带伞 🌧️",
}

def get_weather(city: str, date: str = None) -> dict:
    if date is None:
        date = datetime.date.today().strftime("%Y-%m-%d")
    base = WEATHER_DATA.get(city, {"temp": 20, "condition": "未知", "humidity": 50, "wind": "2级"})
    seed = hash(f"{city}_{date}") % 100
    return {
        "city": city, "date": date,
        "temperature": f"{base['temp'] + (seed % 7 - 3)}°C",
        "condition": base["condition"],
        "humidity": f"{min(100, max(0, base['humidity'] + seed % 15 - 7))}%",
        "wind": base["wind"],
        "advice": ADVICE.get(base["condition"], "注意关注实时预报")
    }

registry.register({
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "获取指定城市的实时天气信息，包括温度、天气状况、湿度等",
        "parameters": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "城市名称"},
                "date": {"type": "string", "description": "日期 YYYY-MM-DD，默认为今天"}
            },
            "required": ["city"]
        }
    }
}, get_weather)

# ─── 计算器工具 ──────────────────────────────
def calculator(expression: str) -> dict:
    allowed = {k: v for k, v in math.__dict__.items() if not k.startswith("__")}
    allowed.update({"abs": abs, "round": round, "min": min, "max": max})
    blacklist = ["__", "import", "exec", "eval", "open"]
    if any(w in expression for w in blacklist):
        return {"error": f"包含非法字符"}
    try:
        result = eval(expression, {"__builtins__": {}}, allowed)
        return {"expression": expression, "result": result, "formatted": f"{expression} = {result}"}
    except Exception as e:
        return {"expression": expression, "error": str(e)}

registry.register({
    "type": "function",
    "function": {
        "name": "calculator",
        "description": "执行精确的数学计算，适合复杂运算",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "数学表达式，如 '123456*789'"}
            },
            "required": ["expression"]
        }
    }
}, calculator)

# ─── 搜索工具 ────────────────────────────────
SEARCH_DB = {
    "亚运": [{"title": "杭州亚运会闭幕", "snippet": "第19届亚运会在杭州圆满落幕..."}],
    "AI": [{"title": "GPT-5 即将发布", "snippet": "OpenAI 宣布下一代模型 GPT-5..."}],
    "天气": [{"title": "全国天气预报", "snippet": "中央气象台发布最新天气预报..."}],
}

def web_search(query: str, num_results: int = 5) -> dict:
    results = []
    for kw, data in SEARCH_DB.items():
        if kw in query:
            results.extend(data)
    return {"results": results[:num_results]} if results else {"message": "未找到相关信息"}

registry.register({
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "搜索实时信息，适合查询最新新闻和资讯",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "搜索关键词"},
                "num_results": {"type": "integer", "description": "返回结果数量"}
            },
            "required": ["query"]
        }
    }
}, web_search)
```

### 6.2 主循环

```python
"""
assistant.py — 智能助手主程序
Function Calling 四步循环：
  用户提问 → 模型判断 → 调工具 → 返回结果 → 模型生成回答
"""

import os, json, requests
from tool_registry import registry

DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"


def call_llm(messages: list, tools: list = None, tool_choice: str = "auto") -> dict:
    """调用 DeepSeek API"""
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": 0.3,
        "max_tokens": 2048,
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = tool_choice

    resp = requests.post(
        DEEPSEEK_URL,
        headers={
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        },
        json=payload,
        timeout=30
    )
    return resp.json()["choices"][0]["message"]


def run_conversation(user_input: str, max_turns: int = 5) -> str:
    """运行一次完整的会话（可包含多轮工具调用）"""
    messages = [
        {"role": "system", "content": "你是一个智能助手，可以使用多种工具帮助用户。"
                                      "调用工具后用自然语言总结结果回复用户。"},
        {"role": "user", "content": user_input}
    ]

    turn = 0
    while turn < max_turns:
        turn += 1
        print(f"\n  ⏳ 第 {turn} 轮推理...")

        # 调用模型
        response = call_llm(messages, registry.get_schemas())

        if "tool_calls" in response and response["tool_calls"]:
            # 模型请求调工具
            messages.append({
                "role": "assistant",
                "content": response.get("content"),
                "tool_calls": response["tool_calls"]
            })

            # 依次执行每个工具
            for tc in response["tool_calls"]:
                func_name = tc["function"]["name"]
                func_args = json.loads(tc["function"]["arguments"])
                tool_call_id = tc["id"]

                print(f"  🔧 调用工具: {func_name}({func_args})")

                # 执行工具
                result = registry.execute(func_name, func_args)

                # 把工具结果返回给模型
                messages.append({
                    "role": "tool",
                    "content": json.dumps(result, ensure_ascii=False),
                    "tool_call_id": tool_call_id
                })

                print(f"  ✅ 工具返回: {str(result)[:80]}...")

        else:
            # 模型直接回答，结束循环
            return response["content"]

    return "⚠️ 达到最大轮数，请简化你的问题。"


def main():
    print("=" * 55)
    print("  🛠️  智能助手 —— Function Calling 演示")
    print("  可用工具: 天气查询 · 计算器 · 网络搜索")
    print("=" * 55)

    if not DEEPSEEK_API_KEY:
        print("\n⚠️  请设置 DEEPSEEK_API_KEY 环境变量")
        return

    print(f"\n📦 已加载 {len(registry.list_tools())} 个工具: {', '.join(registry.list_tools())}")

    examples = [
        "北京的天气怎么样？",
        "计算 123456 × 789012 等于多少？",
        "北京今天和上海的天气对比一下",
        "计算 2 的 32 次方，然后搜索一下 AI 的最新进展",
    ]

    print("\n💡 试试这些问题：")
    for i, ex in enumerate(examples, 1):
        print(f"   {i}. {ex}")

    while True:
        try:
            user_input = input("\n❓ 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break

        if not user_input:
            continue
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 再见！")
            break

        answer = run_conversation(user_input)
        print(f"\n🤖 助手: {answer}")


if __name__ == "__main__":
    main()
```

### 6.3 一次实际的对话流程

```
❓ 你: 北京今天天气怎么样？再算一下 1024 × 768

  ⏳ 第 1 轮推理...
  🔧 调用工具: get_weather({'city': '北京'})
  ✅ 工具返回: {"city": "北京", "temperature": "25°C", "condition": "晴", ...}

  ⏳ 第 2 轮推理...
  🔧 调用工具: calculator({'expression': '1024 * 768'})
  ✅ 工具返回: {"expression": "1024 * 768", "result": 786432, ...}

  ⏳ 第 3 轮推理...
  🤖 助手: 北京今天天气晴朗，温度约 25°C，适合户外活动 ☀️
         另外，1024 × 768 = 786432。
```

---

## 7. 课堂练习

### 练习1：单工具调用

用天气工具分别查询以下城市，对比结果：

```
Q1: 北京的天气怎么样？
Q2: 上海今天有雨吗？
Q3: 北京和杭州哪个更适合户外活动？
```

<details>
<summary>点击查看预期效果</summary>

模型应正确调用 get_weather 工具，基于返回数据给出建议。
北京（晴，25°C）比杭州（小雨，27°C）更适合户外活动。
</details>

### 练习2：多工具组合

```
Q: 深圳天气怎么样？再帮我算 365 × 24 × 60
```

<details>
<summary>点击查看预期效果</summary>

模型应分两步：先调用 get_weather("深圳")，再调用 calculator("365 * 24 * 60")。
最终结果：深圳天气 + 365×24×60 = 525600（一年的分钟数）
</details>

### 练习3：添加自己的工具

在 `tool_registry.py` 中添加一个新工具——**货币转换器**：

```python
def currency_converter(amount: float, from_currency: str, to_currency: str) -> dict:
    """货币转换（模拟汇率）"""
    rates = {"USD": 1.0, "CNY": 7.2, "EUR": 0.92, "JPY": 149.0, "GBP": 0.79}
    if from_currency not in rates or to_currency not in rates:
        return {"error": "不支持的货币"}
    usd_amount = amount / rates[from_currency]
    result = round(usd_amount * rates[to_currency], 2)
    return {
        "from": f"{amount} {from_currency}",
        "to": f"{result} {to_currency}",
        "rate": round(rates[to_currency] / rates[from_currency], 4)
    }
```

<details>
<summary>点击查看参考注册代码</summary>

```python
registry.register({
    "type": "function",
    "function": {
        "name": "currency_converter",
        "description": "货币转换，支持 USD/CNY/EUR/JPY/GBP",
        "parameters": {
            "type": "object",
            "properties": {
                "amount": {"type": "number", "description": "金额"},
                "from_currency": {"type": "string", "description": "源货币"},
                "to_currency": {"type": "string", "description": "目标货币"}
            },
            "required": ["amount", "from_currency", "to_currency"]
        }
    }
}, currency_converter)
```
</details>

### 练习4：对比 tool_choice 参数

修改 `assistant.py` 中的 `tool_choice` 参数，观察行为差异：

```python
# 模式A：模型自主决定
tool_choice = "auto"        # 模型会根据需要调用工具

# 模式B：强制调用工具
tool_choice = "required"    # 即使不需要工具也会调用

# 模式C：指定工具
tool_choice = {"type": "function", "function": {"name": "get_weather"}}  # 只调天气

# 模式D：无工具
# 移除 tools 参数
```

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 关键要点 |
|------|--------|---------|
| **Function Calling** | 模型请求调用工具，开发者执行 | 模型管"决策"，开发者管"执行" |
| **Tool Schema** | 用 JSON Schema 描述工具的"说明书" | name + description + parameters |
| **tool_choice** | 控制模型调工具的策略 | auto / required / none / 指定工具 |
| **tool_calls** | 模型返回的工具调用请求 | id + name + arguments |
| **Tool 消息** | 开发者把工具结果返回给模型 | role="tool" + tool_call_id |
| **多工具编排** | 模型可在一次回复中调多个工具 | 全部执行完一次性返回 |

### Function Calling 最佳实践

1. **description 要写清楚** — 模型靠 description 决定什么时候用哪个工具
2. **参数名要直观** — `city` 比 `loc` 好，模型更容易理解
3. **错误处理要完善** — 工具有可能失败，返回友好的错误信息
4. **限制工具调用次数** — 防止模型无限循环调工具（设置 max_turns）
5. **工具结果要结构化** — 返回 JSON 让模型容易理解
6. **参数的 description 很重要** — 模型靠它来正确填充参数

### 工具设计原则

```
工具命名：动词+名词，如 get_weather、send_email、calculate
参数设计：最小必需原则，只放模型确实需要知道的参数
返回值：结构化 JSON，包含足够的信息让模型生成回答
错误处理：永远返回 dict（不抛异常），包含 error 字段
幂等性：同一个参数多次调用应返回一致结果（至少不破坏数据）
```

### 今日检查清单

- [ ] 理解 Function Calling 的四步循环
- [ ] 理解 Tool Schema 的结构（name + description + parameters）
- [ ] 理解 tool_choice 四种模式的区别
- [ ] 运行 `assistant.py` 测试天气查询
- [ ] 运行 `assistant.py` 测试计算器
- [ ] 运行 `assistant.py` 测试多工具组合
- [ ] 练习3：添加货币转换工具
- [ ] 练习4：对比不同 tool_choice 的行为
- [ ] 在 `~/ai-learning/week2/notes/day9.md` 记录学习笔记

### 明天预告

**第 10 天：结构化输出 📐**

- JSON Mode vs JSON Schema
- 强制结构化输出的三种方法
- Pydantic + LLM 最佳实践
- 实战：从非结构化文本中提取结构化数据

---

> 📝 **学习笔记：** 在 `~/ai-learning/week2/notes/day9.md` 记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 接入一个真实的外部 API（如 GitHub API、新闻 API），替换模拟数据
> 💡 **思考：** Function Calling 让大模型从一个"问答机器"变成了一个"智能调度中心"
