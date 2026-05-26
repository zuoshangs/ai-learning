"""
tool_registry.py — 工具注册和执行器

统一管理所有工具的 Schema 定义和执行逻辑。
新增工具 = 写函数 + 注册，两步搞定。
"""

import math
import datetime


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
        """获取所有工具的 JSON Schema（发给模型用）"""
        return self._schemas

    def execute(self, tool_name: str, arguments: dict) -> dict:
        """执行一个工具"""
        if tool_name not in self._tools:
            return {"error": f"未知工具: {tool_name}"}
        handler = self._tools[tool_name]
        try:
            result = handler(**arguments)
            return result if isinstance(result, dict) else {"result": result}
        except Exception as e:
            return {"error": f"工具执行失败: {str(e)}"}

    def list_tools(self) -> list:
        return list(self._tools.keys())


# ─── 全局注册器 ──────────────────────────────
registry = ToolRegistry()


# ====== 工具1: 天气查询 ======
WEATHER_DATA = {
    "北京": {"temp": 25, "condition": "晴", "humidity": 45, "wind": "3级"},
    "上海": {"temp": 28, "condition": "多云", "humidity": 65, "wind": "4级"},
    "广州": {"temp": 32, "condition": "阵雨", "humidity": 80, "wind": "2级"},
    "深圳": {"temp": 31, "condition": "多云", "humidity": 75, "wind": "3级"},
    "杭州": {"temp": 27, "condition": "小雨", "humidity": 70, "wind": "3级"},
    "成都": {"temp": 26, "condition": "阴", "humidity": 60, "wind": "2级"},
    "武汉": {"temp": 30, "condition": "晴", "humidity": 55, "wind": "3级"},
    "重庆": {"temp": 33, "condition": "晴", "humidity": 50, "wind": "2级"},
}

ADVICE = {
    "晴": "适合户外活动，注意防晒 ☀️",
    "多云": "天气不错，适宜出行 ⛅",
    "阴": "天气阴沉，建议带伞 🌥️",
    "小雨": "建议带伞，路面湿滑注意安全 🌦️",
    "阵雨": "可能有阵雨，随身带伞 🌧️",
}


def get_weather(city: str, date: str = None) -> dict:
    """获取城市天气（模拟数据）"""
    if date is None:
        date = datetime.date.today().strftime("%Y-%m-%d")

    base = WEATHER_DATA.get(city, {"temp": 20, "condition": "未知", "humidity": 50, "wind": "2级"})
    seed = hash(f"{city}_{date}") % 100

    return {
        "city": city,
        "date": date,
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
        "description": "获取指定城市的实时天气信息，包括温度、天气状况、湿度、风力等",
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
}, get_weather)


# ====== 工具2: 计算器 ======
def calculator(expression: str) -> dict:
    """执行精确的数学计算"""
    allowed = {k: v for k, v in math.__dict__.items() if not k.startswith("__")}
    allowed.update({
        "abs": abs, "round": round, "min": min, "max": max,
        "sum": sum, "pow": pow, "int": int, "float": float,
    })

    blacklist = ["__", "import", "exec", "eval", "open", "os", "sys", "subprocess"]
    if any(w in expression for w in blacklist):
        return {"error": "表达式包含非法内容", "expression": expression}

    try:
        result = eval(expression, {"__builtins__": {}}, allowed)
        return {
            "expression": expression,
            "result": result,
            "formatted": f"{expression} = {result}"
        }
    except Exception as e:
        return {"error": str(e), "expression": expression}


registry.register({
    "type": "function",
    "function": {
        "name": "calculator",
        "description": "执行精确的数学计算，适合大模型不擅长的复杂计算、大数乘法、开方、幂运算等",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "数学表达式，例如 '123456 * 789012'、'sqrt(144) + 2**10'、'(1024+2048)/3'"
                }
            },
            "required": ["expression"]
        }
    }
}, calculator)


# ====== 工具3: 网络搜索（模拟） ======
SEARCH_DATABASE = {
    "亚运": [{"title": "杭州亚运会圆满闭幕", "snippet": "第19届亚洲运动会10月8日在杭州圆满落幕，中国代表团以201金创历史最佳成绩。"}],
    "AI": [{"title": "AI大模型竞争白热化", "snippet": "多家科技公司发布新一代AI大模型，开源模型性能直追闭源。"}],
    "GPT": [{"title": "GPT-5研发进展", "snippet": "OpenAI正在训练下一代模型GPT-5，预计将在推理能力上有重大突破。"}],
    "天气": [{"title": "全国天气预报", "snippet": "中央气象台发布最新天气预报，未来三天北方地区将迎来降温。"}],
    "编程": [{"title": "Python 3.13发布", "snippet": "Python 3.13正式发布，带来JIT编译器和自由线程等重大特性。"}],
    "苹果": [{"title": "苹果发布新款iPhone", "snippet": "苹果公司发布iPhone 17系列，搭载全新A19芯片。"}],
}


def web_search(query: str, num_results: int = 5) -> dict:
    """搜索实时信息（模拟数据）"""
    results = []
    for keyword, data in SEARCH_DATABASE.items():
        if keyword in query:
            results.extend(data)

    if results:
        return {"results": results[:num_results]}
    else:
        return {
            "results": [
                {"title": f"关于「{query}」的搜索结果",
                 "snippet": f"这是关于「{query}」的模拟搜索结果。接入真实搜索API后可获取实时数据。"}
            ]
        }


registry.register({
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "搜索实时信息，适合查询最新新闻、资讯、技术动态等",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词，如 'AI最新进展'、'杭州亚运会'"
                },
                "num_results": {
                    "type": "integer",
                    "description": "返回结果数量，默认5条"
                }
            },
            "required": ["query"]
        }
    }
}, web_search)


# ====== 测试代码 ======
if __name__ == "__main__":
    print(f"📦 已注册 {len(registry.list_tools())} 个工具: {', '.join(registry.list_tools())}")
    print()

    # 测试天气
    print("🔧 测试 get_weather('北京')")
    r = registry.execute("get_weather", {"city": "北京"})
    print(f"   {r['temperature']}, {r['condition']}, {r['advice']}")

    # 测试计算器
    print("\n🔧 测试 calculator('123456 * 789012')")
    r = registry.execute("calculator", {"expression": "123456 * 789012"})
    print(f"   {r['formatted']}")

    # 测试搜索
    print("\n🔧 测试 web_search('AI')")
    r = registry.execute("web_search", {"query": "AI"})
    print(f"   找到 {len(r['results'])} 条结果")
    for item in r['results']:
        print(f"   - {item['title']}")
