"""
Day 28: 多工具编排 + Agent 记忆系统 (Python 对照版)

功能：
1. 4+ 工具：天气查询、数学计算、日期时间、文档读写
2. 长短记忆：短期（InMemory）+ 长期（SQLite 文件存储）
3. 错误自修正：工具调用失败时自动重试（最多3次）
4. 编排器：根据用户意图自动选择工具组合
5. 演示场景: "帮我查北京天气，然后计算25°C是多少华氏度，最后记录下来"

依赖：pip install openai requests
"""

import json
import os
import re
import sqlite3
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import requests
from openai import OpenAI


# ==================== 配置 ====================
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "sk-your-key-here")
BASE_URL = "https://api.deepseek.com"
MODEL = "deepseek-chat"
MAX_RETRIES = 3
NOTES_DIR = os.path.expanduser("~/.agent-notes-py")
DB_PATH = os.path.expanduser("~/.agent-memory.db")


# ==================== 记忆系统 ====================
class MemoryService:
    """记忆服务：短期（内存）+ 长期（SQLite）"""

    def __init__(self, db_path: str = DB_PATH):
        self.short_term: Dict[str, List[Dict]] = {}
        self.db_path = db_path
        self._init_db()

    def _init_db(self):
        """初始化 SQLite 长期记忆库"""
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        conn = sqlite3.connect(self.db_path)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS conversation_memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_mem_session
            ON conversation_memory(session_id, created_at)
        """)
        conn.commit()
        conn.close()
        print("📦 长期记忆 (SQLite) 就绪:", self.db_path)

    def get_or_create_session(self, session_id: str) -> List[Dict]:
        """获取或创建短期会话"""
        if session_id not in self.short_term:
            self.short_term[session_id] = [
                {"role": "system", "content": "你是一个智能助手，可以使用多种工具来帮助用户完成任务。"
                 "可用工具：天气查询、数学计算、日期时间、文档读写。"}
            ]
        return self.short_term[session_id]

    def add_short_term(self, session_id: str, role: str, content: str):
        """添加消息到短期记忆"""
        messages = self.get_or_create_session(session_id)
        messages.append({"role": role, "content": content})
        self._trim(session_id)

    def add_long_term(self, session_id: str, role: str, content: str):
        """保存到长期记忆 (SQLite)"""
        try:
            conn = sqlite3.connect(self.db_path)
            conn.execute(
                "INSERT INTO conversation_memory (session_id, role, content) VALUES (?, ?, ?)",
                (session_id, role, content)
            )
            conn.commit()
            conn.close()
        except Exception as e:
            print(f"⚠️ 长期记忆保存失败: {e}")

    def save_message(self, session_id: str, role: str, content: str):
        """保存到短期 + 长期"""
        self.add_short_term(session_id, role, content)
        self.add_long_term(session_id, role, content)

    def get_context(self, session_id: str, max_tokens: int = 4000) -> List[Dict]:
        """获取上下文（短期优先，不足时补充长期）"""
        messages = self.short_term.get(session_id, [])
        if len(messages) > 3:
            return messages

        # 补充长期记忆
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.execute(
                "SELECT role, content FROM conversation_memory WHERE session_id=? ORDER BY created_at ASC",
                (session_id,)
            )
            rows = cursor.fetchall()
            conn.close()

            existing = {(m["role"], m["content"]) for m in messages}
            for role, content in rows:
                if (role, content) not in existing:
                    messages.append({"role": role, "content": content})
        except Exception as e:
            print(f"⚠️ 长期记忆读取失败: {e}")

        return messages

    def clear(self, session_id: str):
        """清空短期记忆"""
        if session_id in self.short_term:
            del self.short_term[session_id]

    def _trim(self, session_id: str, max_tokens: int = 4000):
        """裁剪超过 token 上限的消息"""
        messages = self.short_term.get(session_id, [])
        total_chars = sum(len(m["content"]) for m in messages)
        estimated_tokens = int(total_chars / 1.5)

        while estimated_tokens > max_tokens and len(messages) > 2:
            messages.pop(1)  # 保留系统消息（第一条）
            total_chars = sum(len(m["content"]) for m in messages)
            estimated_tokens = int(total_chars / 1.5)


# ==================== 工具层 ====================
class Tools:
    """所有可用工具的集合"""

    @staticmethod
    def get_weather(city: str) -> str:
        """查询天气"""
        print(f"  🌤 查询天气: {city}")
        try:
            url = f"https://wttr.in/{city}?format=j1"
            resp = requests.get(url, timeout=10)
            data = resp.json()
            cc = data["current_condition"][0]
            temp = cc["temp_C"]
            feels = cc["FeelsLikeC"]
            desc = cc["weatherDesc"][0]["value"]
            humidity = cc["humidity"]
            wind = cc["windspeedKmph"]
            return (f"🌤 {city} 实时天气：\n"
                    f"- 温度：{temp}°C（体感 {feels}°C）\n"
                    f"- 天气：{desc}\n"
                    f"- 湿度：{humidity}%\n"
                    f"- 风速：{wind} km/h")
        except Exception as e:
            return f"❌ 天气查询失败: {e}"

    @staticmethod
    def calculate(expression: str) -> str:
        """数学计算"""
        print(f"  🔢 计算: {expression}")
        # 安全校验
        if not re.match(r'^[\d+\-*/().%\s]+$', expression):
            return "❌ 表达式包含非法字符"
        try:
            result = eval(expression, {"__builtins__": {}}, {})
            return f"✅ {expression} = {result}"
        except Exception as e:
            return f"❌ 计算错误: {e}"

    @staticmethod
    def celsius_to_fahrenheit(celsius: float) -> str:
        """摄氏度转华氏度"""
        print(f"  🌡 温度转换: {celsius}°C")
        fahrenheit = celsius * 9.0 / 5.0 + 32
        return f"✅ {celsius:.1f}°C = {fahrenheit:.1f}°F"

    @staticmethod
    def get_current_datetime() -> str:
        """当前日期时间"""
        now = datetime.now()
        return f"🕐 当前日期时间：{now.strftime('%Y-%m-%d %H:%M:%S')}"

    @staticmethod
    def get_current_date() -> str:
        """当前日期"""
        now = datetime.now()
        return f"📅 当前日期：{now.strftime('%Y-%m-%d (%A)')}"

    @staticmethod
    def days_between(date1: str, date2: str) -> str:
        """计算日期差"""
        try:
            from datetime import datetime
            d1 = datetime.strptime(date1, "%Y-%m-%d")
            d2 = datetime.strptime(date2, "%Y-%m-%d")
            diff = abs((d2 - d1).days)
            return f"📆 {date1} 到 {date2} 相差 {diff} 天"
        except ValueError as e:
            return f"❌ 日期格式错误: {e}"

    @staticmethod
    def save_note(title: str, content: str) -> str:
        """保存笔记"""
        print(f"  📝 保存笔记: {title}")
        os.makedirs(NOTES_DIR, exist_ok=True)
        safe_title = re.sub(r'[\\/:*?"<>|]', '_', title)
        filepath = os.path.join(NOTES_DIR, f"{safe_title}.md")
        note = f"# {title}\n\n创建时间：{datetime.now()}\n---\n\n{content}"
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(note)
        return f"✅ 笔记《{title}》已保存"

    @staticmethod
    def read_note(title: str) -> str:
        """读取笔记"""
        print(f"  📖 读取笔记: {title}")
        safe_title = re.sub(r'[\\/:*?"<>|]', '_', title)
        filepath = os.path.join(NOTES_DIR, f"{safe_title}.md")
        if not os.path.exists(filepath):
            return f"❌ 笔记《{title}》不存在"
        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()

    @staticmethod
    def list_notes() -> str:
        """列出所有笔记"""
        os.makedirs(NOTES_DIR, exist_ok=True)
        notes = sorted(
            [f for f in os.listdir(NOTES_DIR) if f.endswith(".md")],
            key=lambda f: os.path.getmtime(os.path.join(NOTES_DIR, f)),
            reverse=True
        )
        if not notes:
            return "📝 暂无笔记"
        result = f"📚 笔记列表（共 {len(notes)} 篇）：\n\n"
        for note in notes:
            name = note.replace(".md", "")
            size = os.path.getsize(os.path.join(NOTES_DIR, note))
            mtime = datetime.fromtimestamp(
                os.path.getmtime(os.path.join(NOTES_DIR, note))
            ).strftime("%Y-%m-%d %H:%M")
            result += f"  - 《{name}》 ({size} 字符, {mtime})\n"
        return result


# 工具注册表
TOOL_REGISTRY = {
    "getWeather": Tools.get_weather,
    "calculate": Tools.calculate,
    "celsiusToFahrenheit": lambda c: Tools.celsius_to_fahrenheit(float(c)),
    "getCurrentDateTime": lambda: Tools.get_current_datetime(),
    "getCurrentDate": lambda: Tools.get_current_date(),
    "daysBetween": lambda d1, d2: Tools.days_between(d1, d2),
    "saveNote": Tools.save_note,
    "readNote": Tools.read_note,
    "listNotes": lambda: Tools.list_notes(),
}

TOOL_DESCRIPTIONS = """
## WeatherTool
- 功能：查询指定城市的实时天气
- 用法：getWeather("城市名")

## CalculatorTool
- 功能：执行数学计算或温度转换
- 用法：calculate("表达式") 或 celsiusToFahrenheit(25)

## DateTimeTool
- 功能：获取当前日期时间、计算日期差
- 用法：getCurrentDateTime(), daysBetween("2024-01-01", "2024-12-31")

## DocumentTool
- 功能：读写临时笔记
- 用法：saveNote("标题", "内容"), readNote("标题"), listNotes()
"""


# ==================== 编排器 ====================
class OrchestratorAgent:
    """编排器 Agent"""

    def __init__(self):
        self.client = OpenAI(api_key=DEEPSEEK_API_KEY, base_url=BASE_URL)
        self.memory = MemoryService()
        self.system_prompt = self._build_system_prompt()

    def _build_system_prompt(self) -> str:
        return f"""你是一个智能编排器 Agent。你的任务是理解用户意图并选择适当的工具来完成任务。

可用工具：
{TOOL_DESCRIPTIONS}

调用规则：
当你需要调用工具时，请使用以下格式：

TOOL_CALL: 工具名称 | 参数1 | 参数2 | ...

例如：
TOOL_CALL: getWeather | Beijing
TOOL_CALL: calculate | 25 * 9 / 5 + 32
TOOL_CALL: celsiusToFahrenheit | 25
TOOL_CALL: getCurrentDateTime
TOOL_CALL: saveNote | 北京天气记录 | 2024年北京的天气情况...

重要规则：
1. 如果不需要工具，直接回答
2. 多工具时列出所有 TOOL_CALL
3. 先用 THOUGHT: 思考
4. 参数用 | 分隔"""

    def _call_llm(self, messages: List[Dict]) -> str:
        """调用 LLM API"""
        resp = self.client.chat.completions.create(
            model=MODEL,
            messages=messages,
            temperature=0.7
        )
        return resp.choices[0].message.content or ""

    def _parse_tool_calls(self, text: str) -> List[Tuple[str, List[str]]]:
        """解析 TOOL_CALL"""
        calls = []
        pattern = r'TOOL_CALL:\s*(\w+)\s*\|\s*(.*?)(?=TOOL_CALL:|$)'
        for match in re.finditer(pattern, text, re.DOTALL):
            tool_name = match.group(1).strip()
            args_str = match.group(2).strip()
            args = [a.strip().strip("\"'") for a in args_str.split("|") if a.strip()]
            if tool_name in TOOL_REGISTRY:
                calls.append((tool_name, args))
        return calls

    def _execute_tool(self, tool_name: str, args: List[str], retries: int = MAX_RETRIES) -> str:
        """执行工具（带重试）"""
        func = TOOL_REGISTRY.get(tool_name)
        if not func:
            return f"❌ 未知工具: {tool_name}"

        for attempt in range(1, retries + 1):
            try:
                print(f"  🔧 [{attempt}/{retries}] {tool_name}({', '.join(args)})")
                result = func(*args)
                if result.startswith("❌") or result.startswith("错误"):
                    raise RuntimeError(result)
                return result
            except Exception as e:
                print(f"  ⚠️  失败: {e}")
                if attempt < retries:
                    # 尝试修正参数
                    fix_prompt = (
                        f"工具「{tool_name}」调用失败: {e}\n"
                        f"原始参数: {args}\n"
                        f"请给出修正后的参数，只输出修正后的参数列表（逗号分隔）"
                    )
                    fix_resp = self._call_llm([
                        {"role": "system", "content": "你是一个参数修正助手。只输出修正后的参数，逗号分隔。"},
                        {"role": "user", "content": fix_prompt}
                    ])
                    fixed = [a.strip().strip("\"'") for a in fix_resp.split(",")]
                    if fixed:
                        args = fixed
                        print(f"  🔄 修正参数: {args}")

        return f"❌ 工具《{tool_name}》执行失败（已重试{retries}次）"

    def process(self, session_id: str, user_message: str) -> str:
        """处理用户消息"""
        print(f"\n{'='*60}")
        print(f"📩 [{session_id}] 用户: {user_message}")
        print(f"{'='*60}")

        # 1. 保存消息
        self.memory.save_message(session_id, "user", user_message)

        # 2. 获取上下文
        context = self.memory.get_context(session_id)

        # 3. LLM 生成计划
        messages = [
            {"role": "system", "content": self.system_prompt},
            *[{"role": m["role"], "content": m["content"]}
              for m in context if m["role"] != "system"],
            {"role": "user", "content": user_message}
        ]

        print("  🤔 LLM 思考中...")
        plan = self._call_llm(messages)
        print(f"  📋 计划:\n{plan}")

        # 4. 解析并执行工具
        tool_calls = self._parse_tool_calls(plan)
        results = []

        for tool_name, args in tool_calls:
            result = self._execute_tool(tool_name, args)
            results.append(f"【{tool_name}】\n{result}")

        if not results:
            # 纯对话
            self.memory.save_message(session_id, "assistant", plan)
            return plan

        # 5. 生成最终回复
        tool_results = "\n\n".join(results)
        final_prompt = f"用户问题：{user_message}\n\n工具执行结果：\n{tool_results}\n\n请用自然语言总结回答用户。"
        final_resp = self._call_llm([
            {"role": "system", "content": "你是一个智能助手。用自然语言总结工具结果回答用户。"},
            {"role": "user", "content": final_prompt}
        ])

        self.memory.save_message(session_id, "assistant", final_resp)

        print(f"\n✅ 最终回答:\n{final_resp}")
        return final_resp


# ==================== 主函数 ====================
def main():
    """演示场景"""
    agent = OrchestratorAgent()
    session = "demo-session"

    print("\n" + "★" * 50)
    print("  Day 28 Demo: 多工具编排 + Agent 记忆系统")
    print("  Python 对照版")
    print("★" * 50)

    # 演示场景：查天气 → 温度转换 → 记录笔记
    queries = [
        "帮我查一下北京的天气",
        "然后计算 25°C 是多少华氏度",
        "把这两个结果记录下来，标题叫「北京天气与温度转换」",
        "读取刚才保存的笔记"
    ]

    for i, query in enumerate(queries, 1):
        print(f"\n\n{'='*60}")
        print(f"  步骤 {i}/4")
        print(f"{'='*60}")
        agent.process(session, query)
        time.sleep(1)

    print("\n\n" + "★" * 50)
    print("  ✅ 演示完成！")
    print(f"  记忆会话: {session}")
    print(f"  笔记保存: {NOTES_DIR}")
    print(f"  长期记忆: {DB_PATH}")
    print("★" * 50)


if __name__ == "__main__":
    main()
