package com.example.react.core;

import com.example.react.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct Agent 核心 — 手动实现 Thought → Action → Observation 循环。
 * <p>
 * 与 Day 19 的自动工具调用不同，这里我们：
 * 1. 手动构造包含工具描述的系统提示词
 * 2. 发送给 LLM，解析响应中的 Thought/Action/Action Input
 * 3. 手动调用对应工具执行
 * 4. 将 Observation 结果追加到对话历史
 * 5. 重复直到 LLM 给出最终 Answer
 * <p>
 * 这样用户可以清晰地看到 AI 的"思考过程"。
 */
@Service
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final int MAX_ITERATIONS = 10;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * ReAct 步骤记录
     */
    public record ReActStep(
            String thought,
            String action,
            String actionInput,
            String observation
    ) {
    }

    /**
     * ReAct 循环结果
     */
    public record ReActResult(
            String answer,
            List<ReActStep> steps,
            int totalIterations,
            boolean success
    ) {
    }

    public ReActAgent(ChatClient.Builder chatClientBuilder, ToolRegistry toolRegistry) {
        // 不配置默认工具 — 我们手动管理工具调用
        this.chatClient = chatClientBuilder.build();
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行 ReAct 循环
     *
     * @param userMessage 用户消息
     * @return ReAct 循环结果（包含所有步骤和最终答案）
     */
    public ReActResult execute(String userMessage) {
        // 构造系统提示词
        String systemPrompt = buildSystemPrompt();

        // 对话历史（交替的 user/assistant 消息，以及观察结果）
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));

        List<ReActStep> steps = new ArrayList<>();
        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("=== ReAct 迭代 {} ===", iteration);

            // 1. 调用 LLM
            String llmResponse = callLLM(messages);
            log.debug("LLM 响应:\n{}", llmResponse);

            // 2. 检查是否包含最终 Answer
            String answer = extractAnswer(llmResponse);
            if (answer != null) {
                log.info("Agent 给出最终答案");
                return new ReActResult(answer, steps, iteration, true);
            }

            // 3. 提取 Thought 和 Action
            String thought = extractThought(llmResponse);
            String action = extractAction(llmResponse);
            String actionInput = extractActionInput(llmResponse);

            if (action == null) {
                // 没找到 Action — 把整个响应作为最终回答
                log.warn("未找到 Action，将 LLM 响应作为答案");
                return new ReActResult(llmResponse, steps, iteration, true);
            }

            log.info("Thought: {}", thought);
            log.info("Action: {} | Input: {}", action, actionInput);

            // 4. 执行工具
            String observation = executeTool(action, actionInput);
            log.info("Observation: {}", observation);

            // 5. 记录步骤
            steps.add(new ReActStep(thought, action, actionInput, observation));

            // 6. 将 LLM 响应和观察结果追加到对话历史
            messages.add(new Message("assistant", llmResponse));
            messages.add(new Message("user", "Observation: " + observation));
        }

        // 超过最大迭代次数
        log.warn("超过最大迭代次数 ({}), 返回最后 LLM 响应", MAX_ITERATIONS);
        String lastResponse = callLLM(messages);
        return new ReActResult(lastResponse, steps, iteration, false);
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是一个智能助手，通过思考(Thought)→行动(Action)→观察(Observation)循环解决问题。
                
                %s
                
                请严格按以下格式回复（每轮最多一个 Action）：
                Thought: 思考当前情况，分析需要什么信息
                Action: 工具名称
                Action Input: {"参数名": "参数值"}
                
                当获得足够信息后，给出最终答案：
                Thought: 我现在可以回答了
                Answer: 最终答案
                
                注意：
                - Thought 必须提供，描述你的推理过程
                - 如果不需要调用工具，直接给出 Answer
                - Action 和 Action Input 必须写在一行
                - Action Input 必须是 JSON 格式
                """.formatted(toolRegistry.getToolsDescription());
    }

    /**
     * 调用 LLM
     */
    private String callLLM(List<Message> messages) {
        // 将消息列表转换为对话字符串
        StringBuilder promptBuilder = new StringBuilder();
        for (Message msg : messages) {
            promptBuilder.append("<|").append(msg.role()).append("|>\n")
                    .append(msg.content()).append("\n");
        }
        promptBuilder.append("<|assistant|>\n");

        return chatClient.prompt()
                .user(u -> u.text(promptBuilder.toString()))
                .call()
                .content();
    }

    /**
     * 从 LLM 响应中提取 Answer
     */
    private String extractAnswer(String response) {
        Pattern pattern = Pattern.compile(
                "(?:Answer|最终答案)\\s*[:：]\\s*(.*)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 从 LLM 响应中提取 Thought
     */
    private String extractThought(String response) {
        Pattern pattern = Pattern.compile(
                "Thought\\s*[:：]\\s*(.*?)(?=\\n(?:Action|Thought|Answer|最终答案)|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * 从 LLM 响应中提取 Action 名称
     */
    private String extractAction(String response) {
        Pattern pattern = Pattern.compile(
                "Action\\s*[:：]\\s*(\\w+)\\s*",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 从 LLM 响应中提取 Action Input (JSON)
     */
    private String extractActionInput(String response) {
        Pattern pattern = Pattern.compile(
                "Action\\s+Input\\s*[:：]\\s*(\\{.*?\\}|`\\{.*?\\}`)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String input = matcher.group(1);
            // 去除可能的反引号
            input = input.replace("`", "").trim();
            return input;
        }
        // 尝试匹配不带 JSON 花括号的情况 — 可能是纯文本参数
        Pattern altPattern = Pattern.compile(
                "Action\\s+Input\\s*[:：]\\s*(.+?)(?=\\n|$)",
                Pattern.CASE_INSENSITIVE);
        Matcher altMatcher = altPattern.matcher(response);
        if (altMatcher.find()) {
            return altMatcher.group(1).trim();
        }
        return "{}";
    }

    /**
     * 执行工具
     */
    private String executeTool(String action, String actionInput) {
        var toolOpt = toolRegistry.getTool(action);
        if (toolOpt.isEmpty()) {
            return "❌ 错误：未知工具 '" + action + "'，可用工具：" +
                    String.join(", ", toolRegistry.getToolNames());
        }

        try {
            JsonNode args;
            if (actionInput != null && !actionInput.isEmpty() && !actionInput.equals("{}")) {
                args = objectMapper.readTree(actionInput);
            } else {
                args = objectMapper.readTree("{}");
            }
            return toolOpt.get().executor().apply(args);
        } catch (Exception e) {
            log.error("执行工具 {} 失败: {}", action, e.getMessage());
            return "❌ 工具执行出错：" + e.getMessage();
        }
    }

    /**
     * 简单的消息记录
     */
    private record Message(String role, String content) {
    }
}
