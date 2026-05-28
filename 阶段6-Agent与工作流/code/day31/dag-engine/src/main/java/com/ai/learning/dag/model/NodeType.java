package com.ai.learning.dag.model;

/**
 * 节点类型：DAG 工作流中的核心节点分类
 */
public enum NodeType {
    /** 起始节点 — 工作流入口，无依赖 */
    START,
    /** LLM 调用节点 — 调用大模型生成回答 */
    LLM,
    /** 工具调用节点 — 调用外部工具（天气、计算、搜索等） */
    TOOL,
    /** 条件判断节点 — 根据上下文值做分支路由 */
    CONDITION,
    /** 结束节点 — 工作流出口，汇总结果 */
    END
}
