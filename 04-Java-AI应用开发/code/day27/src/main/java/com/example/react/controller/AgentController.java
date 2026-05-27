package com.example.react.controller;

import com.example.react.core.ReActAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct Agent 控制器 — 提供 REST API 展示手动 ReAct 循环过程。
 * <p>
 * 与 Day 19 不同：
 * - Day 19 返回最终结果（AI 内部自动调用工具）
 * - Day 27 返回完整的思考过程（Thought → Action → Observation → Answer）
 * <p>
 * 这样用户能看到 AI 每一步的推理和工具调用过程。
 */
@RestController
public class AgentController {

    private final ReActAgent reactAgent;

    public AgentController(ReActAgent reactAgent) {
        this.reactAgent = reactAgent;
    }

    /**
     * ReAct 对话端点 — 返回完整的思考过程
     * <p>
     * 示例：
     * GET /react?msg=北京今天天气怎么样
     * GET /react?msg=计算 12345 × 6789 等于多少
     * GET /react?msg=现在几点了
     * GET /react?msg=5公里等于多少米
     */
    @GetMapping("/react")
    public Map<String, Object> react(@RequestParam(defaultValue = "你好") String msg) {
        long startTime = System.currentTimeMillis();

        // 执行 ReAct 循环
        ReActAgent.ReActResult result = reactAgent.execute(msg);

        long elapsed = System.currentTimeMillis() - startTime;

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("question", msg);
        response.put("answer", result.answer());
        response.put("total_iterations", result.totalIterations());
        response.put("success", result.success());
        response.put("elapsed_ms", elapsed);

        // 步骤详情
        List<Map<String, String>> steps = result.steps().stream()
                .map(step -> {
                    Map<String, String> stepMap = new HashMap<>();
                    stepMap.put("thought", step.thought());
                    stepMap.put("action", step.action());
                    stepMap.put("action_input", step.actionInput());
                    stepMap.put("observation", step.observation());
                    return stepMap;
                })
                .toList();
        response.put("steps", steps);

        return response;
    }

    /**
     * 简洁版 — 只返回最终答案
     */
    @GetMapping("/react/answer")
    public Map<String, Object> reactAnswer(@RequestParam(defaultValue = "你好") String msg) {
        ReActAgent.ReActResult result = reactAgent.execute(msg);
        Map<String, Object> response = new HashMap<>();
        response.put("answer", result.answer());
        return response;
    }
}
