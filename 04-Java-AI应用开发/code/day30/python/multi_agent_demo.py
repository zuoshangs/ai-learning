"""
Day 30 — 多 Agent 协作系统（Orchestrator-Worker）· Python 对照版

与 Java 版功能完全对应：
- Agent 接口（canHandle + execute）
- 5 个 Worker：天气/计算/搜索/笔记/时间
- Orchestrator：路由 + 容错 + 汇总
"""

import requests
import re
from datetime import datetime
from typing import Optional


# ═══════════════════════════════════════════════════
# Agent 核心抽象
# ═══════════════════════════════════════════════════

class Agent:
    """所有 Agent 的基类"""

    def get_name(self) -> str:
        raise NotImplementedError

    def can_handle(self, text: str) -> bool:
        raise NotImplementedError

    def execute(self, text: str) -> dict:
        """返回 {"success": bool, "data": str, "error": Optional[str]}"""
        raise NotImplementedError


# ═══════════════════════════════════════════════════
# Worker Agent 实现
# ═══════════════════════════════════════════════════

class WeatherWorker(Agent):
    def get_name(self) -> str:
        return "weather"

    def can_handle(self, text: str) -> bool:
        return any(k in text for k in ["天气", "weather", "气温", "温度"])

    def execute(self, text: str) -> dict:
        try:
            city = self._extract_city(text)
            resp = requests.get(
                f"https://wttr.in/{city}?format=%25C+%25t+%25w+%25h",
                timeout=10
            )
            return {"success": True, "data": f"{city}天气: {resp.text.strip()}", "error": None}
        except Exception as e:
            return {"success": False, "data": None, "error": f"天气查询失败: {e}"}

    def _extract_city(self, text: str) -> str:
        idx = text.find("天气")
        if idx > 0:
            before = text[:idx].strip()
            parts = before.split()
            return parts[-1]
        return "北京"


class CalculatorWorker(Agent):
    def get_name(self) -> str:
        return "calculator"

    def can_handle(self, text: str) -> bool:
        return bool(re.search(r"\d+\s*[+\-*/]\s*\d", text))

    def execute(self, text: str) -> dict:
        try:
            expr = self._extract_expr(text)
            result = eval(expr)
            return {"success": True, "data": f"{expr} = {result}", "error": None}
        except Exception as e:
            return {"success": False, "data": None, "error": f"计算失败: {e}"}

    def _extract_expr(self, text: str) -> str:
        m = re.search(r"-?\d+(\.\d+)?\s*[+\-*/]\s*-?\d+(\.\d+)?(\s*[+\-*/]\s*-?\d+(\.\d+)?)*", text)
        return m.group() if m else text


class SearchWorker(Agent):
    def get_name(self) -> str:
        return "search"

    def can_handle(self, text: str) -> bool:
        return any(k in text for k in ["搜索", "查找", "查询", "什么是", "search"])

    def execute(self, text: str) -> dict:
        return {"success": True, "data": f"【搜索 Agent】已收到查询: {text}\n（实际部署时接入百度搜索/DuckDuckGo API）", "error": None}


class NoteWorker(Agent):
    def __init__(self):
        self.notes = []

    def get_name(self) -> str:
        return "note"

    def can_handle(self, text: str) -> bool:
        return any(k in text for k in ["笔记", "记住", "保存", "记录", "note", "备忘"])

    def execute(self, text: str) -> dict:
        if "记住" in text or "保存" in text or "记录" in text:
            parts = re.split(r"[：:，,]", text)
            content = parts[1].strip() if len(parts) > 1 else text
            self.notes.append(content)
            return {"success": True, "data": f"已保存笔记: {content}", "error": None}
        elif "列表" in text or "所有" in text:
            if not self.notes:
                return {"success": True, "data": "暂无笔记", "error": None}
            return {"success": True, "data": "笔记列表:\n" + "\n".join(f"- {n}" for n in self.notes), "error": None}
        return {"success": True, "data": f"当前有 {len(self.notes)} 条笔记", "error": None}


class TimeWorker(Agent):
    def get_name(self) -> str:
        return "time"

    def can_handle(self, text: str) -> bool:
        return any(k in text for k in ["时间", "日期", "现在", "今天", "星期", "几号"])

    def execute(self, text: str) -> dict:
        weekdays = ["星期一", "星期二", "星期三", "星期四",
                    "星期五", "星期六", "星期日"]
        now = datetime.now()
        wd = weekdays[now.weekday()]
        return {"success": True, "data": f"当前时间: {now.strftime('%Y年%m月%d日 %H:%M:%S')} {wd}", "error": None}


# ═══════════════════════════════════════════════════
# Orchestrator Agent
# ═══════════════════════════════════════════════════

class OrchestratorAgent:
    """编排器 Agent —— 路由 + 容错 + 汇总"""

    def __init__(self):
        self.workers = [
            WeatherWorker(),
            CalculatorWorker(),
            SearchWorker(),
            NoteWorker(),
            TimeWorker(),
        ]
        print(f"✅ Orchestrator 就绪: {len(self.workers)} 个 Agent")
        for w in self.workers:
            print(f"   Agent: {w.get_name()}")

    def process(self, text: str) -> dict:
        print(f"\n🔄 Orchestrator 收到: {text}")

        # Step 1: 路由
        selected = [w for w in self.workers if w.can_handle(text)]
        if not selected:
            selected = [w for w in self.workers if w.get_name() == "search"]

        print(f"🎯 选中: {[w.get_name() for w in selected]}")

        # Step 2: 执行（容错）
        results = []
        for worker in selected:
            try:
                result = worker.execute(text)
                results.append(result)
            except Exception as e:
                results.append({"success": False, "data": None, "error": f"异常: {e}"})

        # Step 3: 汇总
        success = sum(1 for r in results if r.get("success"))
        failed = len(results) - success

        summary = "\n---\n".join(
            f"【{w.get_name()}】\n{r.get('data', r.get('error', '未知'))}"
            for w, r in zip(selected, results)
        )

        print(f"✅ 完成: {success} 成功, {failed} 失败")
        return {"success": True, "summary": summary, "details": results}


# ═══════════════════════════════════════════════════
# 测试
# ═══════════════════════════════════════════════════

if __name__ == "__main__":
    orchestrator = OrchestratorAgent()

    test_cases = [
        "北京天气",
        "25 * 40 + 10",
        "现在几点",
        "搜索什么是Dify",
        "记住明天开会",
        "北京天气和25*40",
    ]

    for q in test_cases:
        result = orchestrator.process(q)
        print(f"\n结果:\n{result['summary']}")
        print("=" * 50)
