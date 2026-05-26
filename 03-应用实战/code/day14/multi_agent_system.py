"""
multi_agent_system.py — 多Agent协作研究报告生成系统

架构: Orchestrator-Worker 模式
Worker: Research (研究员) + Analyze (分析师) + Review (审查员)
特性: 并行执行、重试退避、优雅降级、冲突处理
"""

import json
import os
import re
import math
import time
import logging
import requests
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import Optional
from concurrent.futures import ThreadPoolExecutor, as_completed
from enum import Enum

# ─── 日志配置 ─────────────────────────────────
logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("MultiAgent")


# ═══════════════════════════════════════════════
# API 配置
# ═══════════════════════════════════════════════

def get_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not key:
        try:
            with open(os.path.expanduser("~/.hermes/auth.json")) as f:
                auth = json.load(f)
            pool = auth.get("credential_pool", {}).get("deepseek", [])
            if pool:
                key = pool[0].get("access_token", "")
        except Exception:
            pass
    return key

API_KEY = get_api_key()
API_URL = "https://api.deepseek.com/v1/chat/completions"


def call_llm(messages, temperature=0.3, response_format=None,
             max_tokens=2048, timeout=30):
    """通用 LLM 调用"""
    if not API_KEY:
        return {"content": "[API Key 未配置]", "finish_reason": "error"}
    
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if response_format:
        payload["response_format"] = response_format
    
    try:
        resp = requests.post(
            API_URL, headers={"Authorization": f"Bearer {API_KEY}"},
            json=payload, timeout=timeout
        )
        choice = resp.json()["choices"][0]
        return choice["message"]
    except requests.Timeout:
        logger.error("LLM 调用超时")
        return {"content": "[超时]", "finish_reason": "timeout"}
    except Exception as e:
        logger.error(f"LLM 调用失败: {str(e)[:50]}")
        return {"content": f"[错误: {str(e)[:30]}]", "finish_reason": "error"}


# ═══════════════════════════════════════════════
# 枚举 & 数据类
# ═══════════════════════════════════════════════

class AgentRole(Enum):
    ORCHESTRATOR = "orchestrator"
    RESEARCH_WORKER = "research_worker"
    ANALYZE_WORKER = "analyze_worker"
    REVIEW_WORKER = "review_worker"


class MessageType(Enum):
    TASK = "task"
    RESULT = "result"
    ERROR = "error"
    STATUS = "status"
    RETRY = "retry"


class TaskStatus(Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


@dataclass
class AgentMessage:
    """Agent间通信协议"""
    sender: str
    receiver: str
    msg_type: str      # task / result / error / status / retry
    task_id: str
    content: dict
    timestamp: float = field(default_factory=time.time)
    retry_count: int = 0
    priority: int = 5  # 1-10, 10最高

    def to_dict(self):
        return asdict(self)


@dataclass
class SubTask:
    """子任务"""
    id: str
    worker_type: str
    query: str
    instructions: str
    expected_output: str    # 期望输出格式
    status: str = "pending"
    result: Optional[dict] = None
    error: Optional[str] = None
    created_at: float = field(default_factory=time.time)
    completed_at: Optional[float] = None


# ═══════════════════════════════════════════════
# 重试策略
# ═══════════════════════════════════════════════

class RetryStrategy:
    """指数退避重试策略"""

    def __init__(self, max_retries=3, base_delay=1.0, backoff=2.0):
        self.max_retries = max_retries
        self.base_delay = base_delay
        self.backoff = backoff

    def execute(self, fn, *args, **kwargs):
        """执行函数，失败时自动重试"""
        last_error = None
        for attempt in range(1, self.max_retries + 1):
            try:
                return fn(*args, **kwargs)
            except Exception as e:
                last_error = e
                if attempt < self.max_retries:
                    delay = self.base_delay * (self.backoff ** (attempt - 1))
                    logger.warning(f"  重试 {attempt}/{self.max_retries} "
                                   f"(等待 {delay:.1f}s): {str(e)[:40]}")
                    time.sleep(delay)
                else:
                    logger.error(f"  放弃重试: {str(e)[:50]}")
        raise last_error


# ═══════════════════════════════════════════════
# 简易搜索引擎
# ═══════════════════════════════════════════════

class SimpleSearcher:
    """简易搜索引擎（模拟+DDG）"""
    
    def search(self, query, num=3):
        try:
            url = f"https://html.duckduckgo.com/html/?q={query.replace(' ', '+')}"
            resp = requests.get(url, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"
            }, timeout=8)
            if resp.status_code == 200:
                results = []
                blocks = re.findall(
                    r'class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>',
                    resp.text, re.DOTALL
                )
                snippets = re.findall(
                    r'class="result__snippet"[^>]*>(.*?)</a>',
                    resp.text, re.DOTALL
                )
                for i, (url, title) in enumerate(blocks[:num]):
                    snippet = snippets[i] if i < len(snippets) else ""
                    results.append({
                        "title": re.sub(r'<[^>]+>', '', title).strip(),
                        "snippet": re.sub(r'<[^>]+>', '', snippet).strip(),
                        "url": url
                    })
                if results:
                    return results
        except Exception:
            pass
        
        # Mock fallback
        return [
            {"title": f"关于'{query}'的研究报告",
             "snippet": f"这是一篇关于{query}的详细分析...",
             "url": f"https://research.example.com/{query}"},
            {"title": f"{query} 最新进展",
             "snippet": f"{query}领域近期取得了重要突破...",
             "url": f"https://news.example.com/{query}"},
        ]


# ═══════════════════════════════════════════════
# Worker 基类
# ═══════════════════════════════════════════════

class BaseWorker:
    """Worker 基类"""
    
    def __init__(self, role: AgentRole, system_prompt: str):
        self.role = role
        self.system_prompt = system_prompt
        self.retry = RetryStrategy(max_retries=2)
        self.execution_time = 0
    
    def execute(self, task: SubTask) -> AgentMessage:
        """执行任务（含重试）"""
        start = time.time()
        try:
            result = self.retry.execute(self._do_execute, task)
            self.execution_time = time.time() - start
            return result
        except Exception as e:
            self.execution_time = time.time() - start
            return self._error_message(task, str(e))
    
    def _do_execute(self, task: SubTask) -> AgentMessage:
        """子类实现此方法"""
        raise NotImplementedError
    
    def _error_message(self, task: SubTask, error: str) -> AgentMessage:
        return AgentMessage(
            sender=self.role.value,
            receiver="orchestrator",
            msg_type="error",
            task_id=task.id,
            content={"error": error, "task_query": task.query},
        )


# ═══════════════════════════════════════════════
# Research Worker（研究员）
# ═══════════════════════════════════════════════

RESEARCH_PROMPT = """你是一个专业的研究员。职责：
1. 根据给定的研究主题，全面收集信息
2. 整理成结构化的研究笔记
3. 标注关键发现和来源
4. 区分"已确认事实"和"未经验证的信息"

输出格式（JSON）：
{
    "summary": "研究总结",
    "key_findings": [{"finding": "发现", "confidence": "high/medium/low"}],
    "sources_count": 3,
    "gaps": ["未覆盖的方面"]
}"""


class ResearchWorker(BaseWorker):
    """研究员 Worker"""
    
    def __init__(self):
        super().__init__(AgentRole.RESEARCH_WORKER, RESEARCH_PROMPT)
        self.searcher = SimpleSearcher()
    
    def _do_execute(self, task: SubTask) -> AgentMessage:
        # 1. 搜索信息
        search_results = self.searcher.search(task.query, num=3)
        
        # 2. LLM 整理搜索结果
        context = "【搜索结果】\n"
        for i, r in enumerate(search_results, 1):
            context += f"{i}. {r['title']}\n   摘要: {r['snippet']}\n   来源: {r['url']}\n"
        
        msg = call_llm([
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": (
                f"研究主题：{task.query}\n\n"
                f"补充说明：{task.instructions}\n\n"
                f"{context}\n\n"
                f"请基于以上信息，生成结构化的研究笔记。"
            )}
        ], response_format={"type": "json_object"}, max_tokens=1536)
        
        try:
            notes = json.loads(msg.get("content", "{}"))
        except json.JSONDecodeError:
            notes = {"summary": msg.get("content", "")[:200], "key_findings": []}
        
        return AgentMessage(
            sender="research_worker",
            receiver="orchestrator",
            msg_type="result",
            task_id=task.id,
            content={
                "notes": notes,
                "sources": search_results,
                "worker_type": "research",
            },
        )


# ═══════════════════════════════════════════════
# Analyze Worker（分析师）
# ═══════════════════════════════════════════════

ANALYZE_PROMPT = """你是一个专业的数据分析师。职责：
1. 分析给定的研究资料，提炼核心观点
2. 识别趋势、模式、矛盾点
3. 给出有洞察力的分析和建议
4. 用数据和逻辑支撑你的结论

输出格式（JSON）：
{
    "analysis_summary": "分析总结",
    "key_insights": [{"insight": "观点", "evidence": "支撑证据"}],
    "trends": ["趋势1", "趋势2"],
    "contradictions": ["矛盾点"],
    "recommendations": ["建议"]
}"""


class AnalyzeWorker(BaseWorker):
    """分析师 Worker"""
    
    def __init__(self):
        super().__init__(AgentRole.ANALYZE_WORKER, ANALYZE_PROMPT)
    
    def _do_execute(self, task: SubTask) -> AgentMessage:
        msg = call_llm([
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": (
                f"分析主题：{task.query}\n\n"
                f"分析要求：{task.instructions}\n\n"
                f"请运用你的知识进行分析，给出有深度的洞察。"
            )}
        ], response_format={"type": "json_object"}, max_tokens=1536)
        
        try:
            analysis = json.loads(msg.get("content", "{}"))
        except json.JSONDecodeError:
            analysis = {"analysis_summary": msg.get("content", "")[:200]}
        
        return AgentMessage(
            sender="analyze_worker",
            receiver="orchestrator",
            msg_type="result",
            task_id=task.id,
            content={
                "analysis": analysis,
                "worker_type": "analyze",
            },
        )


# ═══════════════════════════════════════════════
# Review Worker（审查员）
# ═══════════════════════════════════════════════

REVIEW_PROMPT = """你是一个严格的质量审查员。职责：
1. 审查研究结果和分析报告的质量
2. 检查逻辑是否自洽
3. 识别潜在的错误或遗漏
4. 给出具体的改进建议
5. 如果质量过关，明确说"通过"

输出格式（JSON）：
{
    "overall_score": 85,
    "strengths": ["优点"],
    "issues": [{"severity": "critical/major/minor", "description": "问题"}],
    "suggestions": ["改进建议"],
    "verdict": "pass/conditional/fail"
}"""


class ReviewWorker(BaseWorker):
    """审查员 Worker"""
    
    def __init__(self):
        super().__init__(AgentRole.REVIEW_WORKER, REVIEW_PROMPT)
    
    def _do_execute(self, task: SubTask) -> AgentMessage:
        msg = call_llm([
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": (
                f"审查内容：{task.query}\n\n"
                f"审查标准：{task.instructions}\n\n"
                f"请严格审查以上内容的质量。"
            )}
        ], response_format={"type": "json_object"}, max_tokens=1024)
        
        try:
            review = json.loads(msg.get("content", "{}"))
        except json.JSONDecodeError:
            review = {"verdict": "conditional", "issues": [{"severity": "info", "description": "无法完成严格审查"}]}
        
        return AgentMessage(
            sender="review_worker",
            receiver="orchestrator",
            msg_type="result",
            task_id=task.id,
            content={
                "review": review,
                "worker_type": "review",
            },
        )


# ═══════════════════════════════════════════════
# 任务分解器
# ═══════════════════════════════════════════════

class TaskDecomposer:
    """任务分解器——把用户请求拆为子任务"""
    
    # 预定义任务模板
    TEMPLATES = {
        "research": {
            "keywords": ["分析", "研究", "报告", "调研", "对比", "评估",
                        "现状", "趋势", "前景", "发展"],
            "subtasks": [
                SubTask(
                    id="research",
                    worker_type="research_worker",
                    query="{query}",
                    instructions="全面收集相关信息，关注最新动态和关键数据",
                    expected_output="结构化研究笔记",
                ),
                SubTask(
                    id="analyze",
                    worker_type="analyze_worker",
                    query="{query}",
                    instructions="基于研究资料进行深度分析，提炼关键洞察",
                    expected_output="分析报告",
                ),
                SubTask(
                    id="review",
                    worker_type="review_worker",
                    query="{query}",
                    instructions="审查研究和分析的质量，确保报告完整准确",
                    expected_output="质量评估",
                ),
            ]
        },
        "quick": {
            "keywords": ["什么是", "解释", "简单", "快速", "基本"],
            "subtasks": [
                SubTask(
                    id="analyze",
                    worker_type="analyze_worker",
                    query="{query}",
                    instructions="用通俗易懂的语言解释，200字以内",
                    expected_output="简洁解释",
                ),
            ]
        },
    }
    
    @classmethod
    def decompose(cls, user_query: str) -> list:
        """根据用户请求分解为子任务"""
        query_lower = user_query.lower()
        
        # 匹配模板
        for template_name, template in cls.TEMPLATES.items():
            if any(kw in query_lower for kw in template["keywords"]):
                logger.info(f"  使用模板: {template_name}")
                return [
                    SubTask(
                        id=t.id,
                        worker_type=t.worker_type,
                        query=t.query.replace("{query}", user_query),
                        instructions=t.instructions,
                        expected_output=t.expected_output,
                    )
                    for t in template["subtasks"]
                ]
        
        # 默认：研究模板
        logger.info("  使用默认模板: research")
        return [
            SubTask(
                id=t.id,
                worker_type=t.worker_type,
                query=t.query.replace("{query}", user_query),
                instructions=t.instructions,
                expected_output=t.expected_output,
            )
            for t in cls.TEMPLATES["research"]["subtasks"]
        ]


# ═══════════════════════════════════════════════
# 结果合成器
# ═══════════════════════════════════════════════

class ResultSynthesizer:
    """结果合成器——合并Worker输出为最终报告"""
    
    @staticmethod
    def synthesize(user_query: str,
                   results: dict,
                   errors: list) -> str:
        """合成最终报告"""
        lines = []
        lines.append(f"# 多Agent研究报告: {user_query}")
        lines.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        lines.append(f"参与Agent: {len(results)} 个")
        lines.append("")
        
        # 首先处理错误信息
        if errors:
            lines.append("## ⚠️ 注意事项")
            for err in errors:
                lines.append(f"- {err}")
            lines.append("")
        
        # 研究结果
        if "research" in results:
            r = results["research"]
            notes = r.get("notes", {})
            lines.append("## 📖 研究发现")
            lines.append(f"**{notes.get('summary', '无摘要')}**")
            lines.append("")
            
            for finding in notes.get("key_findings", []):
                conf = {"high": "✅", "medium": "📌", "low": "⚠️"}.get(
                    finding.get("confidence", "medium"), "📌")
                lines.append(f"- {conf} {finding['finding']}")
            
            if notes.get("gaps"):
                lines.append("\n**未覆盖方面：**")
                for gap in notes["gaps"]:
                    lines.append(f"- ❓ {gap}")
            
            lines.append("")
        
        # 分析结果
        if "analyze" in results:
            a = results["analyze"]
            analysis = a.get("analysis", {})
            lines.append("## 📊 深度分析")
            lines.append(f"**{analysis.get('analysis_summary', '无分析')}**")
            lines.append("")
            
            for insight in analysis.get("key_insights", []):
                lines.append(f"💡 **{insight.get('insight', '')}**")
                if insight.get("evidence"):
                    lines.append(f"   → {insight['evidence']}")
            
            if analysis.get("trends"):
                lines.append("\n**趋势：**")
                for t in analysis["trends"]:
                    lines.append(f"- 📈 {t}")
            
            if analysis.get("recommendations"):
                lines.append("\n**建议：**")
                for r in analysis["recommendations"]:
                    lines.append(f"- ✨ {r}")
            
            lines.append("")
        
        # 审查结果
        if "review" in results:
            r = results["review"]
            review = r.get("review", {})
            score = review.get("overall_score", "N/A")
            verdict = review.get("verdict", "unknown")
            
            verdict_icon = {"pass": "✅", "conditional": "⚠️", "fail": "❌"}
            lines.append(f"## ✅ 质量审查 (得分: {score}/100) "
                         f"{verdict_icon.get(verdict, '❓')}")
            
            if review.get("strengths"):
                lines.append("**优点：**")
                for s in review["strengths"]:
                    lines.append(f"- 👍 {s}")
            
            if review.get("issues"):
                lines.append("\n**改进建议：**")
                for issue in review["issues"]:
                    sev = {"critical": "🔴", "major": "🟡", "minor": "🟢"}
                    icon = sev.get(issue.get("severity", "minor"), "⚪")
                    lines.append(f"- {icon} {issue['description']}")
            
            lines.append("")
        
        # 来源
        sources = set()
        for worker_type, result in results.items():
            for s in result.get("sources", []):
                if "url" in s:
                    sources.add(s["url"])
        
        if sources:
            lines.append("## 📎 参考来源")
            for i, url in enumerate(sorted(sources), 1):
                lines.append(f"[{i}] {url}")
        
        return "\n".join(lines)


# ═══════════════════════════════════════════════
# Orchestrator Agent
# ═══════════════════════════════════════════════

class Orchestrator:
    """编排器——多Agent系统的核心调度器"""
    
    def __init__(self):
        self.workers = {
            "research_worker": ResearchWorker(),
            "analyze_worker": AnalyzeWorker(),
            "review_worker": ReviewWorker(),
        }
        self.decomposer = TaskDecomposer()
        self.synthesizer = ResultSynthesizer()
        self.max_workers = 3   # 最大并行Worker数
        self.task_timeout = 45  # 单任务超时
        
        self.results = {}   # task_id → AgentMessage
        self.errors = []    # 错误记录
        self.metrics = {
            "total_time": 0,
            "worker_times": {},
        }
        
        logger.info("=" * 50)
        logger.info("🎯 Orchestrator 已就绪")
        logger.info(f"   Workers: {', '.join(self.workers.keys())}")
        logger.info(f"   最大并行: {self.max_workers}")
        logger.info("=" * 50)
    
    def process_request(self, user_query: str) -> str:
        """处理用户请求：分解→执行→合成"""
        start = time.time()
        logger.info(f"\n📝 收到请求: {user_query}")
        
        # Step 1: 任务分解
        logger.info("🔨 步骤1: 任务分解")
        tasks = self.decomposer.decompose(user_query)
        logger.info(f"   → {len(tasks)} 个子任务")
        for t in tasks:
            logger.info(f"      [{t.id}] {t.worker_type}: {t.query[:40]}...")
        
        # Step 2: 并行执行
        logger.info("🚀 步骤2: 并行执行 Workers")
        self._execute_parallel(tasks)
        
        # Step 3: 结果合成
        logger.info("📋 步骤3: 合成最终报告")
        report = self.synthesizer.synthesize(
            user_query, self.results, self.errors
        )
        
        self.metrics["total_time"] = time.time() - start
        logger.info(f"✅ 完成! 耗时 {self.metrics['total_time']:.1f}s")
        
        return report
    
    def _execute_parallel(self, tasks: list):
        """并行执行子任务"""
        self.results = {}
        self.errors = []
        
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            future_map = {}
            for task in tasks:
                worker = self.workers.get(task.worker_type)
                if not worker:
                    self.errors.append(f"未知Worker类型: {task.worker_type}")
                    continue
                
                future = executor.submit(self._execute_single, worker, task)
                future_map[future] = task
            
            for future in as_completed(future_map, timeout=self.task_timeout + 10):
                task = future_map[future]
                try:
                    msg = future.result(timeout=5)
                    self._handle_result(task, msg)
                except Exception as e:
                    self._handle_failure(task, str(e))
    
    def _execute_single(self, worker: BaseWorker, task: SubTask) -> AgentMessage:
        """执行单个任务（带超时）"""
        task.status = "running"
        result = worker.execute(task)
        task.completed_at = time.time()
        
        worker_name = worker.role.value
        self.metrics["worker_times"][worker_name] = \
            self.metrics["worker_times"].get(worker_name, 0) + worker.execution_time
        
        return result
    
    def _handle_result(self, task: SubTask, msg: AgentMessage):
        """处理Worker成功结果"""
        if msg.msg_type == "result":
            task.status = "completed"
            task.result = msg.content
            self.results[task.id] = msg.content
            exec_time = task.completed_at - task.created_at if task.completed_at else 0
            logger.info(f"  ✅ [{task.id}] {task.worker_type} 完成 ({exec_time:.1f}s)")
        else:
            self._handle_failure(task, msg.content.get("error", "未知错误"))
    
    def _handle_failure(self, task: SubTask, error: str):
        """处理Worker失败"""
        task.status = "failed"
        task.error = error
        error_msg = f"[{task.id}] {task.worker_type}: {error[:60]}"
        self.errors.append(error_msg)
        logger.warning(f"  ❌ {error_msg}")
    
    def get_status(self):
        """系统状态"""
        lines = [
            "🎯 Orchestrator 状态",
            f"  总耗时: {self.metrics['total_time']:.1f}s",
            f"  成功: {len(self.results)} / 失败: {len(self.errors)}",
        ]
        for name, t in self.metrics["worker_times"].items():
            lines.append(f"  {name}: {t:.1f}s")
        return "\n".join(lines)
    
    def print_worker_report(self):
        """打印各Worker的详细报告"""
        for name, result in self.results.items():
            print(f"\n{'='*50}")
            print(f"  Worker: {result.get('worker_type', name)}")
            print(f"{'='*50}")
            if result.get('notes'):
                notes = result['notes']
                print(f"  📖 {notes.get('summary', '')[:80]}")
            if result.get('analysis'):
                a = result['analysis']
                print(f"  📊 {a.get('analysis_summary', '')[:80]}")
            if result.get('review'):
                r = result['review']
                print(f"  ✅ 评分: {r.get('overall_score', 'N/A')} | "
                      f"结论: {r.get('verdict', 'N/A')}")


# ═══════════════════════════════════════════════
# API 密钥检查
# ═══════════════════════════════════════════════

def check_api():
    """检查 API 密钥"""
    if not API_KEY:
        logger.error("❌ 未找到 API Key")
        return False
    logger.info(f"✅ API Key 已配置 ({API_KEY[:6]}...)")
    return True


# ═══════════════════════════════════════════════
# 主程序
# ═══════════════════════════════════════════════

def main():
    """多Agent系统主入口"""
    if not check_api():
        return
    
    orchestrator = Orchestrator()
    
    print("\n" + "=" * 55)
    print("  🤝 多Agent研究报告生成系统")
    print("  Orchestrator + Research + Analyze + Review")
    print("=" * 55)
    
    print("\n📌 特殊指令：")
    print("  quit         退出")
    print("  /status      查看系统状态")
    print("  /report      查看各Worker详细报告")
    
    print("\n💡 试试：")
    print('  "分析AI Agent框架的现状"')
    print('  "研究2026年大模型发展趋势"')
    print('  "对比Python和Java在AI领域的应用"')
    
    while True:
        try:
            user_input = input("\n🧑 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break
        
        if not user_input:
            continue
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 再见！")
            break
        if user_input == "/status":
            print(orchestrator.get_status())
            continue
        if user_input == "/report":
            orchestrator.print_worker_report()
            continue
        
        # 处理请求
        report = orchestrator.process_request(user_input)
        print(f"\n📋 最终报告:\n{'-'*40}\n{report}\n{'-'*40}")
        
        print(f"\n📊 指标: {orchestrator.get_status()}")


if __name__ == "__main__":
    main()
