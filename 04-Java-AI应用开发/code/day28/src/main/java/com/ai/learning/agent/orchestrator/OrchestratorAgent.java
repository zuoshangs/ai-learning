package com.ai.learning.agent.orchestrator;

import com.ai.learning.agent.memory.ConversationMemory;
import com.ai.learning.agent.memory.MemoryService;
import com.ai.learning.agent.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编排器 Agent — 核心大脑
 * 接收用户请求 → 判断意图 → 选择工具链 → 执行（含重试）→ 结果组合
 * 支持错误自修正（最多3次重试）
 */
@Service
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final OpenAiChatModel chatModel;
    private final MemoryService memoryService;
    private final CalculatorTool calculatorTool;
    private final WeatherTool weatherTool;
    private final DateTimeTool dateTimeTool;
    private final DocumentTool documentTool;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 工具注册表：名称 → 可用工具 */
    private final Map<String, ToolFunction> toolRegistry = new LinkedHashMap<>();

    @FunctionalInterface
    interface ToolFunction {
        String execute(List<String> args);
    }

    public OrchestratorAgent(OpenAiChatModel chatModel,
                             MemoryService memoryService,
                             CalculatorTool calculatorTool,
                             WeatherTool weatherTool,
                             DateTimeTool dateTimeTool,
                             DocumentTool documentTool) {
        this.chatModel = chatModel;
        this.memoryService = memoryService;
        this.calculatorTool = calculatorTool;
        this.weatherTool = weatherTool;
        this.dateTimeTool = dateTimeTool;
        this.documentTool = documentTool;

        // 注册所有工具
        registerTools();
        log.info("编排器初始化完成，已注册 {} 个工具", toolRegistry.size());
    }

    /** 注册全部工具到注册表 */
    private void registerTools() {
        toolRegistry.put("calculate", args -> {
            if (args.isEmpty()) return "错误：缺少表达式参数";
            return calculatorTool.calculate(String.join(" ", args));
        });

        toolRegistry.put("celsiusToFahrenheit", args -> {
            if (args.isEmpty()) return "错误：缺少温度值参数";
            try {
                double celsius = Double.parseDouble(args.get(0));
                return calculatorTool.celsiusToFahrenheit(celsius);
            } catch (NumberFormatException e) {
                return "错误：温度值格式无效: " + args.get(0);
            }
        });

        toolRegistry.put("getWeather", args -> {
            if (args.isEmpty()) return "错误：缺少城市名称参数";
            return weatherTool.getWeather(String.join(" ", args));
        });

        toolRegistry.put("getCurrentDateTime", args -> dateTimeTool.getCurrentDateTime());
        toolRegistry.put("getCurrentDate", args -> dateTimeTool.getCurrentDate());
        toolRegistry.put("getCurrentTime", args -> dateTimeTool.getCurrentTime());
        toolRegistry.put("daysBetween", args -> {
            if (args.size() < 2) return "错误：需要两个日期参数，格式 yyyy-MM-dd";
            return dateTimeTool.daysBetween(args.get(0), args.get(1));
        });

        toolRegistry.put("saveNote", args -> {
            if (args.size() < 2) return "错误：需要标题和内容参数";
            return documentTool.saveNote(args.get(0), args.get(1));
        });

        toolRegistry.put("readNote", args -> {
            if (args.isEmpty()) return "错误：需要笔记标题参数";
            return documentTool.readNote(String.join(" ", args));
        });

        toolRegistry.put("listNotes", args -> documentTool.listNotes());
    }

    /**
     * 处理用户消息的主入口
     * 1. 获取记忆上下文
     * 2. 让 LLM 判断意图并选择工具
     * 3. 执行工具（含重试）
     * 4. 保存到记忆
     * 5. 组合最终响应
     */
    public String processMessage(String sessionId, String userMessage) {
        log.info("===== 处理消息 [会话:{}]: {}", sessionId, userMessage);

        // 1. 保存用户消息到记忆
        memoryService.saveMessage(sessionId, "user", userMessage);

        // 2. 准备上下文
        List<ConversationMemory.Message> context = memoryService.getFullContext(sessionId);

        // 3. LLM 生成工具调用计划
        String plan = generateToolPlan(context, userMessage);
        log.info("LLM 生成计划:\n{}", plan);

        // 4. 执行工具计划
        StringBuilder resultBuilder = new StringBuilder();
        List<ToolCall> toolCalls = parseToolCalls(plan);

        if (toolCalls.isEmpty()) {
            // 纯对话响应（无需工具）
            String response = generateDirectResponse(context, userMessage);
            memoryService.saveMessage(sessionId, "assistant", response);
            return response;
        }

        for (ToolCall call : toolCalls) {
            String toolResult = executeToolWithRetry(call, MAX_RETRIES);
            resultBuilder.append("【").append(call.toolName).append("】\n")
                         .append(toolResult).append("\n\n");
        }

        String toolResults = resultBuilder.toString().trim();

        // 5. LLM 生成最终回复（组合结果）
        String finalResponse = generateFinalResponse(userMessage, toolResults);
        memoryService.saveMessage(sessionId, "assistant", finalResponse);

        log.info("最终响应:\n{}", finalResponse);
        return finalResponse;
    }

    /** 让 LLM 生成工具调用计划 */
    private String generateToolPlan(List<ConversationMemory.Message> context, String userMessage) {
        String systemPrompt = buildSystemPrompt();

        // 构建消息列表
        List<AbstractMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // 添加上下文
        for (ConversationMemory.Message msg : context) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                case "system" -> {} // 已添加
            }
        }

        // 添加当前用户消息（如果不在上下文中）
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages.toArray(new org.springframework.ai.chat.messages.Message[0]));        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText();
        return content != null ? content : "";
    }

    /** 构建系统提示词 */
    private String buildSystemPrompt() {
        return """
            你是一个智能编排器 Agent。你的任务是理解用户意图并选择适当的工具来完成任务。

            ## 可用工具

            """
            + CalculatorTool.getToolDescription() + "\n"
            + WeatherTool.getToolDescription() + "\n"
            + DateTimeTool.getToolDescription() + "\n"
            + DocumentTool.getToolDescription() + "\n"
            + """
            ## 调用规则

            当你需要调用工具时，请使用以下格式（每行一个调用）：

            TOOL_CALL: 工具名称 | 参数1 | 参数2 | ...

            例如：
            TOOL_CALL: getWeather | Beijing
            TOOL_CALL: calculate | 25 * 9 / 5 + 32
            TOOL_CALL: celsiusToFahrenheit | 25
            TOOL_CALL: getCurrentDateTime
            TOOL_CALL: saveNote | 北京天气记录 | 2024年北京的天气情况...
            TOOL_CALL: readNote | 我的笔记
            TOOL_CALL: listNotes
            TOOL_CALL: daysBetween | 2024-01-01 | 2024-12-31

            ## 重要规则

            1. 如果用户问的问题不需要工具，直接回答即可
            2. 如果需要多个工具，列出所有 TOOL_CALL
            3. 可以先思考后调用，思考用 THOUGHT: 开头
            4. 参数用 | 分隔，不要加多余引号
            5. 优先使用 getWeather 查询天气
            6. 优先使用 celsiusToFahrenheit 做温度转换
            7. 优先使用 saveNote 记录信息
            """;
    }

    /** 从 LLM 响应中解析工具调用 */
    private List<ToolCall> parseToolCalls(String plan) {
        List<ToolCall> calls = new ArrayList<>();
        Pattern pattern = Pattern.compile("TOOL_CALL:\\s*(\\w+)\\s*\\|?\\s*(.*?)(?=TOOL_CALL:|$)",
            Pattern.DOTALL);
        Matcher matcher = pattern.matcher(plan);

        while (matcher.find()) {
            String toolName = matcher.group(1).trim();
            String argsStr = matcher.group(2).trim();

            if (toolRegistry.containsKey(toolName)) {
                List<String> args = parseArgs(argsStr);
                calls.add(new ToolCall(toolName, args));
                log.info("解析到工具调用: {} 参数: {}", toolName, args);
            } else {
                log.warn("未知工具: {}", toolName);
            }
        }
        return calls;
    }

    /** 解析参数（按 | 分隔，自动去除多余空白和引号） */
    private List<String> parseArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        if (argsStr == null || argsStr.trim().isEmpty()) {
            return args;
        }

        // 按 | 分割
        String[] parts = argsStr.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            // 去除可能的引号
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            } else if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                args.add(trimmed);
            }
        }
        return args;
    }

    /** 执行工具（带重试机制） */
    private String executeToolWithRetry(ToolCall call, int maxRetries) {
        ToolFunction func = toolRegistry.get(call.toolName);
        if (func == null) {
            return "❌ 未知工具: " + call.toolName;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("执行工具 [第{}次]: {} 参数: {}", attempt, call.toolName, call.args);
                String result = func.execute(call.args);

                // 检查是否返回了错误信息
                if (result.startsWith("错误") || result.startsWith("❌")) {
                    throw new RuntimeException("工具返回错误: " + result);
                }

                return result;
            } catch (Exception e) {
                lastError = e;
                log.warn("工具执行失败 [第{}次/共{}次]: {} - {}",
                    attempt, maxRetries, call.toolName, e.getMessage());

                if (attempt < maxRetries) {
                    // 尝试修正参数
                    String fixedArgs = tryFixArgs(call, e.getMessage());
                    if (fixedArgs != null) {
                        log.info("修正参数: {} → {}", call.args, fixedArgs);
                        call.args = parseArgs(fixedArgs);
                    }
                }
            }
        }

        String errorMsg = "❌ 工具《" + call.toolName + "》执行失败（已重试" + maxRetries + "次）: "
            + (lastError != null ? lastError.getMessage() : "未知错误");
        log.error(errorMsg);
        return errorMsg;
    }

    /** 尝试修正参数（使用 LLM 重新解析） */
    private String tryFixArgs(ToolCall call, String errorMessage) {
        try {
            String fixPrompt = String.format("""
                工具「%s」调用失败，错误信息：%s
                原始参数：%s
                请给出修正后的参数，只输出 TOOL_CALL: %s | <修正后的参数>
                """, call.toolName, errorMessage, call.args, call.toolName);

            Prompt prompt = new Prompt(new UserMessage(fixPrompt));
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();

            if (content != null) {
                Pattern p = Pattern.compile("TOOL_CALL:\\s*" + call.toolName + "\\s*\\|?\\s*(.*?)$", Pattern.DOTALL);
                Matcher m = p.matcher(content);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        } catch (Exception e) {
            log.warn("参数修正失败: {}", e.getMessage());
        }
        return null;
    }

    /** 生成纯对话响应（无需工具时） */
    private String generateDirectResponse(List<ConversationMemory.Message> context, String userMessage) {
        String systemPrompt = "你是一个智能助手。请直接回答用户的问题。保持简洁、友好。";

        List<AbstractMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        for (ConversationMemory.Message msg : context) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        Prompt prompt = new Prompt(messages.toArray(new org.springframework.ai.chat.messages.Message[0]));        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    /** 生成最终回复（组合工具结果） */
    private String generateFinalResponse(String userMessage, String toolResults) {
        String systemPrompt = "你是一个智能助手。用户请求已通过以下工具执行得到结果。"
            + "请用自然语言总结并回答用户。保持简洁清晰。";

        String combinedMessage = "用户问题：" + userMessage + "\n\n工具执行结果：\n" + toolResults;

        Prompt prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(combinedMessage)
        ));

        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    /** 工具调用内部记录 */
    static class ToolCall {
        String toolName;
        List<String> args;

        ToolCall(String toolName, List<String> args) {
            this.toolName = toolName;
            this.args = args;
        }

        @Override
        public String toString() {
            return toolName + "(" + String.join(", ", args) + ")";
        }
    }
}
