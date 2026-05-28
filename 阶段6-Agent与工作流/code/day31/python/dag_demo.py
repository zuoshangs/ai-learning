#!/usr/bin/env python3
"""
Day 31 — Python 对照版：DAG 执行引擎

演示 DAG（有向无环图）在 AI 工作流编排中的核心概念：
1. 节点定义与类型（START/LLM/TOOL/CONDITION/END）
2. 拓扑排序与环检测
3. 分层执行（同层节点可并行）
4. 条件路由

对比 Java 版的 DagGraph + DagExecutor。
"""

import json
import re
import time
from collections import deque, defaultdict
from enum import Enum
from typing import Any, Optional


# ═══════════════════════════════════════════
# 第1层：节点定义
# ═══════════════════════════════════════════

class NodeType(Enum):
    START = "START"
    LLM = "LLM"
    TOOL = "TOOL"
    CONDITION = "CONDITION"
    END = "END"


class DagNode:
    """DAG 节点 — 工作流中的最小执行单元"""

    def __init__(self, node_id: str, node_type: NodeType,
                 dependencies: list[str] = None, config: dict = None):
        self.id = node_id
        self.type = node_type
        self.dependencies = dependencies or []
        self.config = config or {}
        # 运行时状态
        self.output: Any = None
        self.executed: bool = False
        self.error: Optional[str] = None

    def __repr__(self):
        return f"DagNode({self.id}, {self.type.value})"


# ═══════════════════════════════════════════
# 第2层：DAG 图（拓扑排序 + 环检测）
# ═══════════════════════════════════════════

class DagGraph:
    """DAG 图 — 管理节点拓扑"""

    def __init__(self):
        self._nodes: dict[str, DagNode] = {}

    def add_node(self, node: DagNode):
        self._nodes[node.id] = node

    def get_node(self, node_id: str) -> Optional[DagNode]:
        return self._nodes.get(node_id)

    @property
    def nodes(self) -> dict[str, DagNode]:
        return self._nodes

    # ---- Kahn 拓扑排序 ----

    def topological_sort(self) -> list[str]:
        """Kahn 算法拓扑排序，检测环"""
        in_degree = {nid: 0 for nid in self._nodes}
        for node in self._nodes.values():
            for dep in node.dependencies:
                in_degree[node.id] = in_degree.get(node.id, 0) + 1

        queue = deque([nid for nid, deg in in_degree.items() if deg == 0])
        sorted_nodes = []

        while queue:
            nid = queue.popleft()
            sorted_nodes.append(nid)
            # 找下游
            for node in self._nodes.values():
                if nid in node.dependencies:
                    in_degree[node.id] -= 1
                    if in_degree[node.id] == 0:
                        queue.append(node.id)

        if len(sorted_nodes) != len(self._nodes):
            cycle_nodes = set(self._nodes.keys()) - set(sorted_nodes)
            raise ValueError(f"DAG 中存在环！参与环的节点: {cycle_nodes}")

        return sorted_nodes

    # ---- DFS 环检测 ----

    def detect_cycle(self) -> list[str]:
        """DFS 法检测环，返回环路径"""
        WHITE, GRAY, BLACK = 0, 1, 2
        color = {nid: WHITE for nid in self._nodes}
        path = []

        def dfs(nid: str) -> Optional[list[str]]:
            color[nid] = GRAY
            path.append(nid)
            for dep in self._nodes[nid].dependencies:
                if color.get(dep) == GRAY:
                    idx = path.index(dep)
                    return path[idx:]
                if color.get(dep) == WHITE:
                    result = dfs(dep)
                    if result:
                        return result
            color[nid] = BLACK
            path.pop()
            return None

        for nid in self._nodes:
            if color[nid] == WHITE:
                result = dfs(nid)
                if result:
                    return result
        return []

    # ---- 分层执行计划 ----

    def get_leveled_plan(self) -> list[list[str]]:
        """按依赖深度分层，同层可并行"""
        sorted_nodes = self.topological_sort()
        depth = {}
        for nid in sorted_nodes:
            node = self._nodes[nid]
            max_dep = max((depth.get(d, -1) for d in node.dependencies), default=-1)
            depth[nid] = max_dep + 1

        levels = defaultdict(list)
        for nid in sorted_nodes:
            levels[depth[nid]].append(nid)
        return [levels[i] for i in sorted(levels)]

    # ---- 验证 ----

    def validate(self):
        """全面验证 DAG"""
        for node in self._nodes.values():
            for dep in node.dependencies:
                if dep not in self._nodes:
                    raise ValueError(f"节点 '{node.id}' 依赖的 '{dep}' 不存在")

        cycle = self.detect_cycle()
        if cycle:
            raise ValueError(f"DAG 存在环: {' → '.join(cycle)}")

        self.topological_sort()  # 双重确认

        types = [n.type for n in self._nodes.values()]
        if NodeType.START not in types:
            raise ValueError("DAG 必须包含 START 节点")
        if NodeType.END not in types:
            raise ValueError("DAG 必须包含 END 节点")

        for node in self._nodes.values():
            if node.type == NodeType.START and node.dependencies:
                raise ValueError(f"START 节点 '{node.id}' 不能有前置依赖")

    def __str__(self):
        lines = ["DagGraph:"]
        for node in self._nodes.values():
            deps = f"  ← {', '.join(node.dependencies)}" if node.dependencies else ""
            lines.append(f"  {node.id} [{node.type.value}]{deps}")
        return "\n".join(lines)


# ═══════════════════════════════════════════
# 第3层：上下文
# ═══════════════════════════════════════════

class DagContext:
    """执行上下文 — 节点间传递数据"""

    def __init__(self):
        self._data: dict[str, Any] = {}

    def set(self, key: str, value: Any):
        self._data[key] = value

    def get(self, key: str, default: Any = None) -> Any:
        return self._data.get(key, default)

    def get_all(self) -> dict:
        return dict(self._data)

    def __contains__(self, key: str) -> bool:
        return key in self._data


# ═══════════════════════════════════════════
# 第4层：执行引擎
# ═══════════════════════════════════════════

class DagExecutor:
    """DAG 执行引擎 — 按分层计划执行工作流"""

    def __init__(self):
        self._tools = {
            "weather": self._call_weather,
            "calculator": self._evaluate_math,
            "web_search": self._simulate_search,
        }

    def execute(self, graph: DagGraph, user_input: str) -> dict:
        """执行完整 DAG 工作流"""
        start_time = time.time()
        graph.validate()
        levels = graph.get_leveled_plan()
        context = DagContext()
        context.set("_input", user_input)

        print(f"\n📋 DAG 验证通过，共 {graph.nodes} 个节点")
        print(f"📋 执行计划: {levels}")

        failed_nodes = []

        for level_idx, level in enumerate(levels):
            print(f"\n▶️ 第 {level_idx + 1} 层: {', '.join(level)}")
            for node_id in level:
                node = graph.get_node(node_id)
                if node is None:
                    continue
                try:
                    self._execute_node(node, context)
                    print(f"  ✅ {node.id} [{node.type.value}] → {self._truncate(str(node.output), 80)}")
                except Exception as e:
                    node.error = str(e)
                    failed_nodes.append(node.id)
                    print(f"  ❌ {node.id} 失败: {e}")

        elapsed = (time.time() - start_time) * 1000
        final_output = context.get("_output", "完成")

        # 收集节点输出
        node_outputs = {}
        for node in graph.nodes.values():
            if node.executed:
                entry = {"type": node.type.value, "output": node.output}
                if node.error:
                    entry["error"] = node.error
                node_outputs[node.id] = entry

        print(f"\n✅ 工作流完成，耗时 {elapsed:.0f}ms")
        return {
            "success": len(failed_nodes) == 0,
            "finalOutput": final_output,
            "nodeOutputs": node_outputs,
            "elapsedMs": elapsed,
            "failedNodes": failed_nodes,
        }

    def _execute_node(self, node: DagNode, ctx: DagContext):
        """分派节点到对应的执行器"""
        handlers = {
            NodeType.START: self._exec_start,
            NodeType.LLM: self._exec_llm,
            NodeType.TOOL: self._exec_tool,
            NodeType.CONDITION: self._exec_condition,
            NodeType.END: self._exec_end,
        }
        handler = handlers.get(node.type)
        if not handler:
            raise ValueError(f"未知节点类型: {node.type}")

        result = handler(node, ctx)
        node.output = result
        node.executed = True
        ctx.set(node.id, result)

    # ---- 节点处理器 ----

    def _exec_start(self, node: DagNode, ctx: DagContext) -> dict:
        return {"message": "工作流开始", "input": ctx.get("_input")}

    def _exec_llm(self, node: DagNode, ctx: DagContext) -> dict:
        prompt_template = node.config.get("prompt", "")
        prompt = self._render(prompt_template, ctx)
        print(f"    🤖 LLM prompt: {self._truncate(prompt, 100)}")

        # 模拟 LLM 流式思考
        response = f"[模拟LLM] 关于「{self._truncate(prompt, 60)}」的回答"
        # 如果 prompt 包含天气或计算的关键词，做"智能"响应
        if "weather" in prompt.lower() or "天气" in prompt:
            response = "需要搜索"
        elif "25*40" in prompt or "计算" in prompt:
            response = "直接回答"

        return {"response": response, "prompt": prompt}

    def _exec_tool(self, node: DagNode, ctx: DagContext) -> dict:
        tool_name = node.config.get("toolName", "unknown")
        params_template = node.config.get("params", "")
        params = self._render(params_template, ctx)

        print(f"    🛠️ 工具: {tool_name}({params})")

        handler = self._tools.get(tool_name)
        result = handler(params) if handler else f"[未知工具: {tool_name}]"
        return {"tool": tool_name, "result": result}

    def _exec_condition(self, node: DagNode, ctx: DagContext) -> dict:
        condition = node.config.get("condition", "")
        true_branch = node.config.get("trueBranch", "")
        false_branch = node.config.get("falseBranch", "")

        result = self._eval_condition(condition, ctx)
        chosen = true_branch if result else false_branch
        print(f"    🔀 条件: {condition} → {result} (走 {chosen})")
        ctx.set(f"_condition_{node.id}_branch", chosen)
        return {"condition": condition, "result": result, "branch": chosen}

    def _exec_end(self, node: DagNode, ctx: DagContext) -> dict:
        output_template = node.config.get("output", "完成")
        final = self._render(output_template, ctx)
        ctx.set("_output", final)
        return {"message": final}

    # ---- 工具实现 ----

    def _call_weather(self, city: str) -> str:
        """模拟天气查询"""
        import requests
        try:
            from urllib.parse import quote
            url = f"https://wttr.in/{quote(city)}?format=%C+%t+%w+%h"
            resp = requests.get(url, timeout=5)
            return f"{city} 天气: {resp.text.strip()}"
        except Exception as e:
            return f"[模拟] {city} 天气: 晴 +25°C"

    def _evaluate_math(self, expr: str) -> str:
        """安全计算器"""
        if not re.match(r'^[0-9+\-*/().%\s]+$', expr):
            return f"不支持的表达式: {expr}"
        try:
            result = eval(expr, {"__builtins__": {}}, {})
            return f"{expr} = {result}"
        except Exception as e:
            return f"计算错误: {e}"

    def _simulate_search(self, query: str) -> str:
        return f"[模拟搜索] 「{query}」的搜索结果摘要"

    # ---- 辅助方法 ----

    def _render(self, template: str, ctx: DagContext) -> str:
        result = template
        for key, value in ctx.get_all().items():
            placeholder = "{" + key + "}"
            result = result.replace(placeholder, str(value))
        return result

    def _eval_condition(self, condition: str, ctx: DagContext) -> bool:
        if not condition:
            return True
        # contains
        if " contains " in condition:
            var, val = condition.split(" contains ", 1)
            return val in str(ctx.get(var.strip(), ""))
        # equals
        if " equals " in condition:
            var, val = condition.split(" equals ", 1)
            return str(ctx.get(var.strip(), "")) == val.strip()
        # notNull
        if condition.endswith(" notNull"):
            var = condition[:-len(" notNull")].strip()
            return ctx.get(var) is not None
        return False

    @staticmethod
    def _truncate(s: str, max_len: int = 80) -> str:
        s = str(s)
        return s[:max_len] + "..." if len(s) > max_len else s


# ═══════════════════════════════════════════
# 第5层：从 JSON 加载工作流
# ═══════════════════════════════════════════

def load_workflow_from_json(json_str: str) -> DagGraph:
    """从 JSON 字符串加载工作流"""
    data = json.loads(json_str)
    graph = DagGraph()
    for node_def in data["nodes"]:
        node_type = NodeType(node_def["type"].upper())
        node = DagNode(
            node_id=node_def["id"],
            node_type=node_type,
            dependencies=node_def.get("dependencies", []),
            config=node_def.get("config", {}),
        )
        graph.add_node(node)
    return graph


def load_workflow_from_file(path: str) -> DagGraph:
    with open(path, encoding="utf-8") as f:
        return load_workflow_from_json(f.read())


def build_sample_workflow() -> DagGraph:
    """代码构建版——等价于 JSON 文件"""
    graph = DagGraph()
    graph.add_node(DagNode("start", NodeType.START))
    graph.add_node(DagNode("weather", NodeType.TOOL, ["start"],
                           {"toolName": "weather", "params": "{_input}"}))
    graph.add_node(DagNode("calculator", NodeType.TOOL, ["start"],
                           {"toolName": "calculator", "params": "{_input}"}))
    graph.add_node(DagNode("summary", NodeType.LLM, ["weather", "calculator"],
                           {"prompt": "总结: 天气={weather}, 计算={calculator}, 输入={_input}"}))
    graph.add_node(DagNode("end", NodeType.END, ["summary"],
                           {"output": "工作流完成: {_summary}"}))
    return graph


def build_qa_workflow() -> DagGraph:
    """问答工作流（带条件分支）"""
    graph = DagGraph()
    graph.add_node(DagNode("start", NodeType.START))
    graph.add_node(DagNode("analyze", NodeType.LLM, ["start"],
                           {"prompt": "分析输入: {_input}，回答'需要搜索'或'直接回答'"}))
    graph.add_node(DagNode("needs_search", NodeType.CONDITION, ["analyze"],
                           {"condition": "analyze contains 需要搜索",
                            "trueBranch": "search_tool",
                            "falseBranch": "direct_answer"}))
    graph.add_node(DagNode("search_tool", NodeType.TOOL, ["needs_search"],
                           {"toolName": "web_search", "params": "{_input}"}))
    graph.add_node(DagNode("direct_answer", NodeType.LLM, ["needs_search"],
                           {"prompt": "请直接回答: {_input}"}))
    graph.add_node(DagNode("final_answer", NodeType.LLM, ["search_tool", "direct_answer"],
                           {"prompt": "综合给出最终答案"}))
    graph.add_node(DagNode("end", NodeType.END, ["final_answer"],
                           {"output": "问答完成: {_summary}"}))
    return graph


# ═══════════════════════════════════════════
# 第6层：演示
# ═══════════════════════════════════════════

def demo_1_weather_and_math():
    """演示 1：天气 + 计算（并行执行）"""
    print("=" * 60)
    print("🎯 演示 1：天气 + 计算（并行 DAG）")
    print("=" * 60)

    graph = build_sample_workflow()
    print(f"\n📊 DAG 结构:\n{graph}")

    plan = graph.get_leveled_plan()
    print(f"\n📋 分层执行计划: {plan}")

    executor = DagExecutor()
    result = executor.execute(graph, "北京天气和25*40等于多少")

    print(f"\n📌 最终输出: {result['finalOutput']}")
    print(f"⏱️ 耗时: {result['elapsedMs']:.0f}ms")
    print(f"✅ 成功: {result['success']}")


def demo_2_qa_with_branch():
    """演示 2：问答工作流（条件分支）"""
    print("\n" + "=" * 60)
    print("🎯 演示 2：问答工作流（条件分支）")
    print("=" * 60)

    graph = build_qa_workflow()
    print(f"\n📊 DAG 结构:\n{graph}")

    plan = graph.get_leveled_plan()
    print(f"\n📋 分层执行计划: {plan}")

    executor = DagExecutor()
    result = executor.execute(graph, "今天天气怎么样")

    print(f"\n📌 最终输出: {result['finalOutput']}")
    print(f"⏱️ 耗时: {result['elapsedMs']:.0f}ms")


def demo_3_cycle_detection():
    """演示 3：环检测"""
    print("\n" + "=" * 60)
    print("🎯 演示 3：环检测")
    print("=" * 60)

    graph = DagGraph()
    graph.add_node(DagNode("a", NodeType.START))
    graph.add_node(DagNode("b", NodeType.LLM, ["a"]))
    graph.add_node(DagNode("c", NodeType.LLM, ["b"]))
    graph.add_node(DagNode("d", NodeType.END, ["c"]))
    # 故意加环：a → b → c → a
    graph.get_node("a").dependencies = ["c"]

    cycle = graph.detect_cycle()
    print(f"\n🔍 环检测结果: {' → '.join(cycle) if cycle else '无环'}")

    try:
        graph.validate()
        print("验证通过")
    except ValueError as e:
        print(f"❌ 验证失败: {e}")

    # 修好后重试
    graph.get_node("a").dependencies = []  # 去掉环
    graph.validate()
    print("✅ 修复后验证通过")


def demo_4_json_workflow():
    """演示 4：从 JSON 加载工作流"""
    print("\n" + "=" * 60)
    print("🎯 演示 4：从 JSON 加载工作流")
    print("=" * 60)

    json_str = json.dumps({
        "name": "简单问候工作流",
        "nodes": [
            {"id": "start", "type": "START", "dependencies": [], "config": {}},
            {"id": "greet", "type": "LLM", "dependencies": ["start"],
             "config": {"prompt": "用中文问候: {_input}"}},
            {"id": "end", "type": "END", "dependencies": ["greet"],
             "config": {"output": "完成: {_summary}"}},
        ]
    }, ensure_ascii=False, indent=2)

    print(f"\n📄 JSON 定义:\n{json_str}")
    graph = load_workflow_from_json(json_str)
    print(f"\n📊 加载的 DAG:\n{graph}")

    executor = DagExecutor()
    result = executor.execute(graph, "Hello World")
    print(f"\n📌 输出: {result['finalOutput']}")


if __name__ == "__main__":
    print("🐍 Python DAG 执行引擎 演示")
    print("=" * 60)

    demo_1_weather_and_math()
    demo_2_qa_with_branch()
    demo_3_cycle_detection()
    demo_4_json_workflow()

    print("\n" + "=" * 60)
    print("✨ 全部演示完成！")
