#!/usr/bin/env python3
"""
Day 27: ReAct Agent 手动循环 — Python 对照版

与 Java 版对应，展示 Thought → Action → Observation 循环的核心原理。
通过 OpenAI/DeepSeek 兼容 API 实现手动 ReAct 循环。

用法：
    python react_agent_demo.py

环境变量：
    DEEPSEEK_API_KEY — DeepSeek API 密钥
"""

import os
import json
import re
import sys
from datetime import datetime

import requests

# === 配置 ===
API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
BASE_URL = "https://api.deepseek.com"
MODEL = "deepseek-chat"
MAX_ITERATIONS = 10


# === 工具函数 ===

def calculate(expression: str) -> str:
    """执行数学计算"""
    try:
        # 清理表达式
        cleaned = expression.replace("×", "*").replace("x", "*").replace("X", "*") \
            .replace("÷", "/").replace("＋", "+").replace("－", "-").strip()

        # 解析运算符
        result = None
        op_used = ""
        for op in ["+", "-", "*", "/"]:
            if op in cleaned:
                # 只分割第一个运算符
                idx = cleaned.find(op)
                # 确保不是负数
                if idx > 0 or op != "-":
                    left = cleaned[:idx].strip()
                    right = cleaned[idx + 1:].strip()
                    a, b = float(left), float(right)
                    op_used = op

                    if op == "+":
                        result = a + b
                    elif op == "-":
                        result = a - b
                    elif op == "*":
                        result = a * b
                    elif op == "/":
                        if b == 0:
                            return "❌ 除数不能为零"
                        result = a / b
                    break

        if result is None:
            return "❌ 无法识别的运算符或表达式格式错误"

        # 格式化
        if result == int(result):
            return f"📊 计算结果\n──────────\n{left} {op_used} {right} = {int(result)}"
        else:
            return f"📊 计算结果\n──────────\n{left} {op_used} {right} = {result:.2f}"

    except Exception as e:
        return f"❌ 计算出错：{e}"


def get_weather(city: str) -> str:
    """查询天气（模拟数据）"""
    weather_data = {
        "北京": ("☀️ 晴", "25°C", "40%", "北风 2 级", "55（良）"),
        "上海": ("⛅ 多云", "28°C", "68%", "东南风 3 级", "42（优）"),
        "深圳": ("🌦️ 阵雨", "30°C", "82%", "南风 4 级", "25（优）"),
        "广州": ("🌧️ 小雨", "27°C", "78%", "东南风 3 级", "31（优）"),
    }

    if city in weather_data:
        weather, temp, humidity, wind, pm = weather_data[city]
        return (f"🌡️ {city} 当前天气\n"
                f"────────────────\n"
                f"天气：{weather}\n"
                f"温度：{temp}（体感 23°C）\n"
                f"湿度：{humidity}\n"
                f"风力：{wind}\n"
                f"PM2.5：{pm}")
    else:
        return (f"🌡️ {city} 当前天气\n"
                f"────────────────\n"
                f"天气：☀️ 晴\n"
                f"温度：23°C（体感 22°C）\n"
                f"湿度：50%\n"
                f"风力：微风")


def get_forecast(city: str) -> str:
    """获取天气预报（模拟数据）"""
    forecasts = {
        "北京": "🌤️ 明天：晴 18°C ~ 27°C\n⛅ 后天：多云 20°C ~ 25°C\n🌧️ 大后天：小雨 16°C ~ 22°C",
        "上海": "⛅ 明天：多云 22°C ~ 29°C\n🌦️ 后天：阵雨 21°C ~ 26°C\n☀️ 大后天：晴 19°C ~ 28°C",
    }
    forecast = forecasts.get(city,
                             "☀️ 明天：21°C ~ 28°C 晴\n⛅ 后天：20°C ~ 26°C 多云\n☀️ 大后天：19°C ~ 27°C 晴")
    return f"📅 {city} 未来3天预报\n─────────────────\n{forecast}"


def get_current_time() -> str:
    """获取当前时间"""
    now = datetime.now()
    weekday_map = ["一", "二", "三", "四", "五", "六", "日"]
    weekday = weekday_map[now.weekday()]
    return (f"🕐 当前时间\n"
            f"─────────\n"
            f"日期：{now.strftime('%Y年%m月%d日')}\n"
            f"时间：{now.strftime('%H:%M:%S')}\n"
            f"星期：{weekday}")


def convert_unit(value: str, from_unit: str, to_unit: str) -> str:
    """单位换算"""
    try:
        val = float(value)

        # 长度换算表 (转成米)
        length_to_m = {
            "米": 1, "m": 1,
            "千米": 1000, "公里": 1000, "km": 1000,
            "厘米": 0.01, "cm": 0.01,
            "英尺": 0.3048, "ft": 0.3048,
            "英寸": 0.0254, "in": 0.0254,
        }

        # 重量换算表 (转成千克)
        weight_to_kg = {
            "克": 0.001, "g": 0.001,
            "千克": 1, "公斤": 1, "kg": 1,
            "斤": 0.5,
            "磅": 0.45359237, "lb": 0.45359237,
            "吨": 1000, "t": 1000,
        }

        # 温度特殊处理
        if from_unit in ("摄氏度", "℃") and to_unit in ("华氏度", "℉"):
            result = val * 9 / 5 + 32
            return f"📐 单位换算结果\n────────────\n{value} {from_unit} = {result:.1f} ℉"
        elif from_unit in ("华氏度", "℉") and to_unit in ("摄氏度", "℃"):
            result = (val - 32) * 5 / 9
            return f"📐 单位换算结果\n────────────\n{value} {from_unit} = {result:.1f} ℃"

        # 长度换算
        if from_unit in length_to_m and to_unit in length_to_m:
            standard = val * length_to_m[from_unit]
            result = standard / length_to_m[to_unit]
        elif from_unit in weight_to_kg and to_unit in weight_to_kg:
            standard = val * weight_to_kg[from_unit]
            result = standard / weight_to_kg[to_unit]
        else:
            return f"❌ 不支持的单位对：{from_unit} → {to_unit}"

        formatted = int(result) if result == int(result) else f"{result:.2f}"
        return f"📐 单位换算结果\n────────────\n{value} {from_unit} = {formatted} {to_unit}"

    except ValueError:
        return "❌ 数值格式错误"
    except Exception as e:
        return f"❌ {e}"


# === 工具注册 ===

TOOLS = {
    "calculate": {
        "description": "执行数学计算，支持加(+)、减(-)、乘(*)、除(/)，传入表达式如 '123 * 456'",
        "fn": lambda args: calculate(args.get("expression", "")),
    },
    "convert_unit": {
        "description": "单位换算，支持长度(米/千米/英尺)、重量(克/千克/斤/磅)、温度(摄氏/华氏)",
        "fn": lambda args: convert_unit(
            args.get("value", ""),
            args.get("from_unit", ""),
            args.get("to_unit", ""),
        ),
    },
    "get_weather": {
        "description": "查询指定城市的当前天气情况",
        "fn": lambda args: get_weather(args.get("city", "")),
    },
    "get_forecast": {
        "description": "获取指定城市未来3天的天气预报",
        "fn": lambda args: get_forecast(args.get("city", "")),
    },
    "get_current_time": {
        "description": "获取当前的日期和时间（无需参数）",
        "fn": lambda args: get_current_time(),
    },
}


def get_tool_params(name: str) -> list:
    """获取工具的参数描述"""
    param_descs = {
        "calculate": ["expression: str"],
        "convert_unit": ["value: str", "from_unit: str", "to_unit: str"],
        "get_weather": ["city: str"],
        "get_forecast": ["city: str"],
        "get_current_time": [],
    }
    return param_descs.get(name, [])


def build_system_prompt() -> str:
    """构建系统提示词"""
    tool_descs = "\n".join(
        f"- {name}({', '.join(get_tool_params(name))}): {info['description']}"
        for name, info in TOOLS.items()
    )

    return f"""你是一个智能助手，通过思考(Thought)→行动(Action)→观察(Observation)循环解决问题。

可用工具：
{tool_descs}

请严格按以下格式回复（每轮最多一个 Action）：
Thought: 思考当前情况，分析需要什么信息
Action: 工具名称
Action Input: {{"参数名": "参数值"}}

当获得足够信息后，给出最终答案：
Thought: 我现在可以回答了
Answer: 最终答案

注意：
- Thought 必须提供，描述你的推理过程
- 如果不需要调用工具，直接给出 Answer
- Action 和 Action Input 必须写在一行
- Action Input 必须是 JSON 格式"""


# === LLM 调用 ===

def call_llm(messages: list) -> str:
    """调用 DeepSeek API"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": MODEL,
        "messages": [
            {"role": msg["role"], "content": msg["content"]}
            for msg in messages
        ],
        "temperature": 0.3,
        "max_tokens": 2000,
    }

    try:
        resp = requests.post(
            f"{BASE_URL}/chat/completions",
            headers=headers,
            json=payload,
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]
    except Exception as e:
        return f"Error calling LLM: {e}"


# === ReAct 循环 ===

def extract_answer(response: str) -> str | None:
    """从 LLM 响应中提取最终答案"""
    match = re.search(
        r"(?:Answer|最终答案)\s*[:：]\s*(.*)",
        response,
        re.DOTALL | re.IGNORECASE,
    )
    return match.group(1).strip() if match else None


def extract_thought(response: str) -> str:
    """提取 Thought"""
    match = re.search(
        r"Thought\s*[:：]\s*(.*?)(?=\n(?:Action|Thought|Answer|最终答案)|$)",
        response,
        re.DOTALL | re.IGNORECASE,
    )
    return match.group(1).strip() if match else ""


def extract_action(response: str) -> str | None:
    """提取 Action 名称"""
    match = re.search(r"Action\s*[:：]\s*(\w+)\s*", response, re.IGNORECASE)
    return match.group(1).strip() if match else None


def extract_action_input(response: str) -> str:
    """提取 Action Input (JSON)"""
    match = re.search(
        r"Action\s+Input\s*[:：]\s*(\{.*?\}|`\{.*?\}`)",
        response,
        re.DOTALL | re.IGNORECASE,
    )
    if match:
        return match.group(1).replace("`", "").strip()

    # 备选：纯文本参数
    match = re.search(
        r"Action\s+Input\s*[:：]\s*(.+?)(?=\n|$)",
        response,
        re.IGNORECASE,
    )
    return match.group(1).strip() if match else "{}"


def execute_tool(action: str, action_input: str) -> str:
    """执行工具"""
    if action not in TOOLS:
        available = ", ".join(TOOLS.keys())
        return f"❌ 错误：未知工具 '{action}'，可用工具：{available}"

    try:
        args = json.loads(action_input) if action_input and action_input != "{}" else {}
        return TOOLS[action]["fn"](args)
    except json.JSONDecodeError:
        return f"❌ JSON 解析错误：{action_input}"
    except Exception as e:
        return f"❌ 工具执行出错：{e}"


def run_react(user_message: str) -> dict:
    """运行完整的 ReAct 循环"""
    import time
    start_time = time.time()

    messages = [
        {"role": "system", "content": build_system_prompt()},
        {"role": "user", "content": user_message},
    ]

    steps = []
    iteration = 0

    print(f"\n{'='*60}")
    print(f"🤖 用户问题: {user_message}")
    print(f"{'='*60}\n")

    while iteration < MAX_ITERATIONS:
        iteration += 1
        print(f"\n{'─'*50}")
        print(f"📌 第 {iteration} 轮迭代")
        print(f"{'─'*50}")

        # 1. 调用 LLM
        response = call_llm(messages)
        print(f"\n💬 LLM 响应:\n{response}\n")

        # 2. 检查最终答案
        answer = extract_answer(response)
        if answer:
            print(f"{'='*60}")
            print(f"✅ 最终答案: {answer}")
            print(f"{'='*60}")
            elapsed = (time.time() - start_time) * 1000
            return {
                "answer": answer,
                "steps": steps,
                "total_iterations": iteration,
                "success": True,
                "elapsed_ms": round(elapsed),
            }

        # 3. 提取 Thought & Action
        thought = extract_thought(response)
        action = extract_action(response)
        action_input = extract_action_input(response)

        if not action:
            print(f"⚠️ 未找到 Action，将响应作为答案")
            elapsed = (time.time() - start_time) * 1000
            return {
                "answer": response,
                "steps": steps,
                "total_iterations": iteration,
                "success": True,
                "elapsed_ms": round(elapsed),
            }

        print(f"🧠 Thought: {thought}")
        print(f"🔧 Action: {action}")
        print(f"📥 Input: {action_input}")

        # 4. 执行工具
        observation = execute_tool(action, action_input)
        print(f"👁️ Observation:\n{observation}")

        # 5. 记录步骤
        steps.append({
            "thought": thought,
            "action": action,
            "action_input": action_input,
            "observation": observation,
        })

        # 6. 追加到对话历史
        messages.append({"role": "assistant", "content": response})
        messages.append({"role": "user", "content": f"Observation: {observation}"})

    # 超过最大迭代次数
    print(f"\n⚠️ 超过最大迭代次数 ({MAX_ITERATIONS})")
    response = call_llm(messages)
    elapsed = (time.time() - start_time) * 1000
    return {
        "answer": response,
        "steps": steps,
        "total_iterations": iteration,
        "success": False,
        "elapsed_ms": round(elapsed),
    }


# === 主函数 ===

def main():
    if not API_KEY:
        print("❌ 请设置环境变量 DEEPSEEK_API_KEY")
        print("   export DEEPSEEK_API_KEY='sk-...'")
        sys.exit(1)

    test_questions = [
        "北京今天天气怎么样？",
        "计算 12345 × 6789 等于多少？",
        "现在几点了？",
        "5公里等于多少米？",
    ]

    for q in test_questions:
        result = run_react(q)
        print(f"\n📊 统计: {result['total_iterations']} 轮迭代, "
              f"耗时 {result['elapsed_ms']}ms, "
              f"成功={result['success']}")
        print()


if __name__ == "__main__":
    main()
