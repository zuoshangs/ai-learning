"""
第19天：工具调用 / Function Calling — Python 对照版

演示 Function Calling 的核心流程：
1. 定义工具（天气查询、计算器、搜索）
2. 调用 API 让模型决定使用哪个工具
3. 执行工具函数并返回结果给模型
4. 模型基于工具结果生成最终回答
"""
import requests
import json
import os
import re

# ============================================================
# 配置
# ============================================================
API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    with open(os.path.expanduser("~/.hermes/.env")) as f:
        for line in f:
            if "DEEPSEEK_API_KEY" in line:
                API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                break

URL = "https://api.deepseek.com/v1/chat/completions"
HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# ============================================================
# 第一步：定义工具（Tool Definition / JSON Schema）
# ============================================================
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市的当前天气情况，返回温度、湿度、风力等信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市名称，如：北京、上海、深圳"
                    }
                },
                "required": ["city"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "calculate",
            "description": "执行数学计算，支持加(+)、减(-)、乘(*)、除(/)",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {
                        "type": "string",
                        "description": "数学表达式，格式如 '123 * 456'"
                    }
                },
                "required": ["expression"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_web",
            "description": "搜索最新信息或新闻，返回相关结果列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "搜索关键词"
                    }
                },
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "convert_unit",
            "description": "单位换算，支持长度(米/千米/英尺)、重量(克/千克/斤/磅)",
            "parameters": {
                "type": "object",
                "properties": {
                    "value": {"type": "string", "description": "数值"},
                    "from_unit": {"type": "string", "description": "原单位"},
                    "to_unit": {"type": "string", "description": "目标单位"}
                },
                "required": ["value", "from_unit", "to_unit"]
            }
        }
    }
]

# ============================================================
# 第二步：实现工具函数
# ============================================================
def get_weather(city: str) -> str:
    """模拟天气查询"""
    data = {
        "北京": "☀️ 晴，25°C，湿度40%，北风2级",
        "上海": "⛅ 多云，28°C，湿度65%，东南风3级",
        "深圳": "🌦️ 阵雨，30°C，湿度80%，南风4级",
        "广州": "🌧️ 小雨，27°C，湿度78%，东南风3级"
    }
    weather = data.get(city, f"☀️ 晴，23°C，湿度50%，微风")
    return f"🌡️ {city}：{weather}"


def calculate(expression: str) -> str:
    """执行数学计算"""
    expression = expression.replace("×", "*").replace("x", "*").replace("÷", "/")
    # 安全解析：只允许数字和运算符
    if not re.match(r'^[\d\s\+\-\*\/\.]+$', expression):
        return "❌ 表达式包含非法字符"
    try:
        # 简单解析：只支持一个运算符
        for op in ['+', '-', '*', '/']:
            if op in expression:
                parts = expression.split(op)
                if len(parts) == 2:
                    a, b = float(parts[0]), float(parts[1])
                    if op == '/':
                        if b == 0:
                            return "❌ 除数不能为零"
                        result = a / b
                    elif op == '*':
                        result = a * b
                    elif op == '+':
                        result = a + b
                    else:
                        result = a - b
                    formatted = int(result) if result == int(result) else round(result, 2)
                    return f"📊 {a} {op} {b} = {formatted}"
        return "❌ 无法解析表达式"
    except Exception as e:
        return f"❌ 计算出错: {e}"


def search_web(query: str) -> str:
    """模拟搜索结果"""
    results = {
        "AI": """
1. DeepSeek发布新一代大模型，性能对标GPT-4o
2. Spring AI 1.0.0 正式发布
3. 全球AI治理框架达成初步共识
        """,
        "Java": """
1. Spring AI 1.0.0-M6 新增 @Tool 注解支持
2. Java 21 LTS 虚拟线程性能提升显著
3. 企业级Java AI应用最佳实践白皮书
        """
    }
    for key, content in results.items():
        if key.lower() in query.lower():
            return f"🔍 搜索结果「{query}」:\n{content}"
    return f"🔍 搜索「{query}」: 找到3条相关结果（模拟数据）"


def convert_unit(value: str, from_unit: str, to_unit: str) -> str:
    """单位换算"""
    conversions = {
        ("公里", "米"): lambda v: v * 1000,
        ("千米", "米"): lambda v: v * 1000,
        ("米", "公里"): lambda v: v / 1000,
        ("米", "千米"): lambda v: v / 1000,
        ("公斤", "斤"): lambda v: v * 2,
        ("千克", "斤"): lambda v: v * 2,
        ("斤", "公斤"): lambda v: v / 2,
        ("斤", "千克"): lambda v: v / 2,
    }
    try:
        val = float(value)
        for (f, t), fn in conversions.items():
            if f == from_unit and t == to_unit:
                result = fn(val)
                return f"📐 {value}{from_unit} = {result}{to_unit}"
        return f"📐 {value}{from_unit} = {value}换算为{to_unit}（无内置公式）"
    except:
        return "❌ 换算出错"


# 工具映射表
TOOL_FUNCTIONS = {
    "get_weather": get_weather,
    "calculate": calculate,
    "search_web": search_web,
    "convert_unit": convert_unit
}


# ============================================================
# 第三步：核心 — Function Calling 循环
# ============================================================
def call_with_tools(messages, max_tool_rounds=5):
    """
    调用 API 并自动处理工具调用循环
    
    流程：
    用户消息 → 模型响应 → 如需要工具 → 执行工具 → 返回结果 → 模型再回答
    """
    for round_num in range(max_tool_rounds):
        print(f"\n  🔄 第 {round_num + 1} 轮调用...")
        
        payload = {
            "model": "deepseek-chat",
            "messages": messages,
            "tools": TOOLS,
            "tool_choice": "auto",
            "temperature": 0.3,
            "max_tokens": 2000
        }
        
        resp = requests.post(URL, headers=HEADERS, json=payload, timeout=60)
        data = resp.json()
        choice = data["choices"][0]
        message = choice["message"]
        finish_reason = choice.get("finish_reason", "")
        
        # 检查是否有工具调用
        tool_calls = message.get("tool_calls", [])
        
        if not tool_calls:
            # 模型直接回答，不需要工具
            print(f"  ✅ 最终回答完成")
            return message["content"]
        
        # 添加模型消息到历史
        messages.append(message)
        
        # 逐个执行工具调用
        print(f"  🔧 模型请求调用 {len(tool_calls)} 个工具...")
        for tc in tool_calls:
            func = tc["function"]
            name = func["name"]
            args = json.loads(func["arguments"])
            
            print(f"     → {name}({json.dumps(args, ensure_ascii=False)})")
            
            # 执行工具
            if name in TOOL_FUNCTIONS:
                result = TOOL_FUNCTIONS[name](**args)
            else:
                result = f"❌ 未知工具: {name}"
            
            print(f"     ← {result[:60]}...")
            
            # 将工具结果返回给模型
            messages.append({
                "role": "tool",
                "tool_call_id": tc["id"],
                "content": result
            })
    
    return "⚠️ 达到最大工具调用轮数"


# ============================================================
# 第四步：测试
# ============================================================
print("=" * 65)
print("  第19天：工具调用 / Function Calling 演示")
print("=" * 65)

test_cases = [
    [{"role": "user", "content": "北京今天天气怎么样？"}],
    [{"role": "user", "content": "计算 12345 × 6789 等于多少？"}],
    [{"role": "user", "content": "帮我搜一下最新的AI新闻"}],
    [{"role": "user", "content": "5公里等于多少米？"}],
    [{"role": "user", "content": "北京天气怎么样？顺便帮我搜一下Java的最新动态"}]
]

for i, messages in enumerate(test_cases, 1):
    print(f"\n{'=' * 60}")
    print(f"📝 测试 {i}: {messages[0]['content']}")
    print(f"{'=' * 60}")
    
    result = call_with_tools(messages)
    
    print(f"\n📌 最终回答:")
    print(f"{result}")

print(f"\n{'=' * 50}")
print("  ✅ Python 测试完成")
print(f"{'=' * 50}")
