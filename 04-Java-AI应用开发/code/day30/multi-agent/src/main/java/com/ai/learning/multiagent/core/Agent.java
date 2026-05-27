package com.ai.learning.multiagent.core;

/**
 * 所有 Agent 必须实现的接口。
 * 
 * 每个 Agent 是一个独立的能力单元，只负责自己擅长的任务。
 * Orchestrator 负责把任务分配给合适的 Agent。
 */
public interface Agent {

    /** Agent 的唯一名称 */
    String getName();

    /**
     * 判断此 Agent 是否能处理该消息
     */
    boolean canHandle(AgentMessage message);

    /**
     * 执行任务 —— 错误被此方法自行捕获并返回 AgentResult.fail()
     * 不会抛出异常，确保调用方不需要 try-catch
     */
    AgentResult execute(AgentMessage message);
}