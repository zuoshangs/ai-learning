package com.ai.learning.dag.executor;

import com.ai.learning.dag.model.DagNode;
import com.ai.learning.dag.model.NodeType;
import com.ai.learning.dag.graph.DagGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * DAG 执行引擎 — 按分层计划执行工作流
 *
 * 执行流程：
 * 1. 验证 DAG（环检测、依赖完整性）
 * 2. 计算分层执行计划
 * 3. 逐层执行：同层节点并发执行，每层结束后传递上下文
 * 4. 条件节点根据上下文路由下游执行分支
 * 5. 返回最终上下文（含所有节点输出）
 */
@Component
public class DagExecutor {
    private static final Logger log = LoggerFactory.getLogger(DagExecutor.class);

    private final ChatClient chatClient;

    public DagExecutor(ChatClient.Builder chatBuilder) {
        // 允许 LLM 节点自定义 system prompt
        this.chatClient = chatBuilder
                .defaultSystem("你是一个 AI 工作流引擎中的 LLM 节点。"
                        + "根据用户输入和上下文回答问题。保持简洁。")
                .build();
    }

    /**
     * 执行完整 DAG 工作流
     *
     * @param graph 已构建好的 DAG 图
     * @param initialInput 用户初始输入
     * @return 执行结果上下文（含每个节点的 output）
     */
    public DagResult execute(DagGraph graph, String initialInput) {
        long startTime = System.currentTimeMillis();

        // 1. 验证 DAG
        graph.validate();
        log.info("✅ DAG 验证通过，共 {} 个节点", graph.size());

        // 2. 获取分层执行计划
        List<List<String>> levels;
        try {
            levels = graph.getLeveledExecutionPlan();
        } catch (IllegalArgumentException e) {
            return DagResult.failure("拓扑排序失败: " + e.getMessage());
        }
        log.info("📋 执行计划共 {} 层: {}", levels.size(),
                levels.stream().map(l -> String.join(",", l)).collect(Collectors.joining(" | ")));

        // 3. 执行上下文
        DagContext context = new DagContext();
        context.set("_input", initialInput);
        context.set("_startTime", startTime);

        // 跟踪失败的节点
        List<String> failedNodes = new ArrayList<>();

        // 4. 逐层执行
        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            List<String> level = levels.get(levelIdx);
            log.info("▶️ 执行第 {} 层: {}", levelIdx + 1, String.join(", ", level));

            // 同层节点并发执行
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            Map<String, DagNode> levelNodes = new LinkedHashMap<>();

            for (String nodeId : level) {
                DagNode node = graph.getNode(nodeId);
                if (node == null) continue;
                levelNodes.put(nodeId, node);

                // 重置运行时状态
                node.setExecuted(false);
                node.setOutput(null);
                node.setError(null);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        executeNode(node, context);
                    } catch (Exception e) {
                        node.setError(e.getMessage());
                        failedNodes.add(node.getId());
                        log.error("❌ 节点 {} 执行失败: {}", node.getId(), e.getMessage());
                    }
                });

                futures.add(future);
            }

            // 等待本层所有节点完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 检查是否有 LLM 节点让条件触发
            handleConditionalExecution(level, graph, context);

            log.info("✅ 第 {} 层完成", levelIdx + 1);
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 收集输出
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        for (DagNode node : graph.getAllNodes()) {
            if (node.isExecuted()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", node.getType());
                entry.put("output", node.getOutput());
                if (node.getError() != null) {
                    entry.put("error", node.getError());
                }
                nodeOutputs.put(node.getId(), entry);
            }
        }

        // 找到 END 节点的输出
        String finalOutput = context.get("_output", "");

        return new DagResult(
                true,
                finalOutput,
                nodeOutputs,
                context.getAll(),
                elapsed,
                failedNodes
        );
    }

    /**
     * 执行单个节点
     */
    private void executeNode(DagNode node, DagContext context) throws Exception {
        log.info("  🔧 执行节点: {} [{}]", node.getId(), node.getType());

        Object result = switch (node.getType()) {
            case START -> executeStart(node, context);
            case LLM   -> executeLlm(node, context);
            case TOOL  -> executeTool(node, context);
            case CONDITION -> executeCondition(node, context);
            case END   -> executeEnd(node, context);
        };

        node.setOutput(result);
        node.setExecuted(true);

        // 把节点输出写入上下文（前提条件节点会用）
        context.set(node.getId(), result);
    }

    // ========== 节点执行器 ==========

    /**
     * START 节点：只是初始化，传递输入
     */
    private Object executeStart(DagNode node, DagContext context) {
        String input = context.get("_input", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "工作流开始");
        result.put("input", input);
        return result;
    }

    /**
     * LLM 节点：调用 AI 模型
     */
    private Object executeLlm(DagNode node, DagContext context) throws IOException {
        String promptTemplate = node.getConfigString("prompt", "请回答用户问题");
        String model = node.getConfigString("model", "default");

        // 模板替换：{input} → 用户输入, {varName} → 上下文变量
        String prompt = renderTemplate(promptTemplate, context);
        log.info("    🤖 LLM 请求 (model={}): {}", model, truncate(prompt, 100));

        // 调用 DeepSeek API
        String response;
        try {
            response = callLLMApi(prompt);
        } catch (Exception e) {
            log.warn("    ⚠️ LLM API 调用失败，使用模拟响应: {}", e.getMessage());
            response = "[模拟LLM响应] 关于「" + truncate(prompt, 50) + "」的回答";
        }

        log.info("    ✅ LLM 响应: {}", truncate(response, 100));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", response);
        result.put("model", model);
        result.put("prompt", prompt);
        return result;
    }

    /**
     * TOOL 节点：调用外部工具
     */
    private Object executeTool(DagNode node, DagContext context) throws Exception {
        String toolName = node.getConfigString("toolName", "unknown");
        String params = node.getConfigString("params", "");
        String renderedParams = renderTemplate(params, context);

        log.info("    🛠️ 工具调用: {} (params={})", toolName, renderedParams);

        Object result = switch (toolName) {
            case "weather" -> callWeatherApi(renderedParams);
            case "calculator" -> evaluateMath(renderedParams);
            case "web_search" -> simulateSearch(renderedParams);
            default -> "[未知工具: " + toolName + "]";
        };

        log.info("    ✅ 工具结果: {}", truncate(String.valueOf(result), 100));
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("tool", toolName);
        toolResult.put("result", result);
        return toolResult;
    }

    /**
     * CONDITION 节点：根据上下文判断走向
     * 条件表达式格式: "${varName} operator value"
     * 例如: "weather_contains 雨" 或 "result != null" 或 "length > 100"
     */
    private Object executeCondition(DagNode node, DagContext context) {
        String condition = node.getConfigString("condition", "");
        String trueBranch = node.getConfigString("trueBranch", "");
        String falseBranch = node.getConfigString("falseBranch", "");

        boolean result = evaluateCondition(condition, context);
        log.info("    🔀 条件判断: {} → {}", condition, result);

        Map<String, Object> condResult = new LinkedHashMap<>();
        condResult.put("condition", condition);
        condResult.put("result", result);
        condResult.put("branch", result ? trueBranch : falseBranch);
        return condResult;
    }

    /**
     * END 节点：汇总最终输出
     */
    private Object executeEnd(DagNode node, DagContext context) {
        // 先构建摘要，设置到上下文中
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Object> entry : context.getAll().entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) entry.getValue();
                if (map.containsKey("response")) {
                    summary.append("LLM 回答: ").append(map.get("response")).append("\n");
                } else if (map.containsKey("result")) {
                    summary.append("工具结果: ").append(map.get("result")).append("\n");
                }
            }
        }
        if (!summary.isEmpty()) {
            context.set("_summary", summary.toString().trim());
        }

        // 再渲染模板（此时 {_summary} 已存在）
        String outputTemplate = node.getConfigString("output", "工作流执行完成");
        String finalOutput = renderTemplate(outputTemplate, context);

        context.set("_output", finalOutput);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", finalOutput);
        result.put("duration", System.currentTimeMillis() - (Long) context.get("_startTime", 0L) + "ms");
        return result;
    }

    // ========== 条件路由处理 ==========

    /**
     * 处理条件节点的分支路由。
     * 如果本层有 CONDITION 节点执行了，根据其路由结果决定下层的哪些节点要跳过。
     */
    private void handleConditionalExecution(List<String> level, DagGraph graph, DagContext context) {
        for (String nodeId : level) {
            DagNode node = graph.getNode(nodeId);
            if (node == null || node.getType() != NodeType.CONDITION) continue;
            if (!node.isExecuted() || node.getError() != null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> condResult = (Map<String, Object>) node.getOutput();
            String chosenBranch = (String) condResult.getOrDefault("branch", "");

            if (!chosenBranch.isEmpty()) {
                log.info("    → 条件路由选择: {}", chosenBranch);
                context.set("_condition_" + nodeId + "_branch", chosenBranch);
            }
        }
    }

    // ========== 工具实现 ==========

    /**
     * 调用天气 API（wttr.in — 无需 API key）
     */
    private String callWeatherApi(String city) throws IOException {
        // 提取城市名：只取纯中文部分或第一个单词
        String cityName = city.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z\\s]", "").trim();
        if (cityName.isEmpty()) {
            cityName = city.split("[^\\u4e00-\\u9fa5a-zA-Z]")[0].trim();
        }
        if (cityName.isEmpty()) {
            return "无法识别的城市: " + city;
        }

        String encoded = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
        // 手动拼接URL避免URI.create()对%的转义问题
        String urlStr = "https://wttr.in/" + encoded + "?format=%25C+%25t+%25w+%25h";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int code = conn.getResponseCode();
        if (code != 200) {
            return "天气API返回状态码: " + code;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String raw = br.readLine();
            return city + " 天气: " + (raw != null ? raw.trim() : "未知");
        }
    }

    /**
     * 数学计算（使用内置脚本引擎）
     */
    private String evaluateMath(String expression) throws Exception {
        // 安全过滤：只允许数字和基本运算符
        if (!expression.matches("[0-9+\\-*/().%\\s]+")) {
            return "不支持的表达式（仅支持数字和 + - * / ( ) 运算符）";
        }
        // 使用 Java 脚本引擎
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
        if (engine == null) {
            return "脚本引擎不可用，请使用 JDK ≤ 14";
        }
        Object result = engine.eval(expression);
        return expression + " = " + result;
    }

    /**
     * 模拟搜索
     */
    private String simulateSearch(String query) {
        return "[" + query + "] 的搜索结果摘要（模拟）";
    }

    // ========== LLM API 调用 ==========

    /**
     * 通过 Spring AI ChatClient 调用 DeepSeek
     */
    private String callLLMApi(String prompt) throws IOException {
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return response != null ? response.trim() : "";
        } catch (Exception e) {
            log.warn("Spring AI 调用失败: {}", e.getMessage());
            throw e;
        }
    }

    // ========== 工具方法 ==========

    /**
     * 模板渲染：将 {varName} 替换为上下文中的值
     */
    private String renderTemplate(String template, DagContext context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.getAll().entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 条件表达式求值
     * 支持: contains, equals, notNull, length_gt, startsWith 等
     */
    private boolean evaluateCondition(String condition, DagContext context) {
        if (condition == null || condition.isBlank()) return true;

        // 查找上下文中匹配的变量
        String trimmed = condition.trim();

        // 格式1: "varName contains 值"
        if (trimmed.contains(" contains ")) {
            String[] parts = trimmed.split(" contains ", 2);
            String varValue = context.getAsString(parts[0].trim(), "");
            return varValue.contains(parts[1].trim());
        }

        // 格式2: "varName equals 值"
        if (trimmed.contains(" equals ")) {
            String[] parts = trimmed.split(" equals ", 2);
            String varValue = context.getAsString(parts[0].trim(), "");
            return varValue.equals(parts[1].trim());
        }

        // 格式3: "varName notNull"
        if (trimmed.endsWith(" notNull")) {
            String varName = trimmed.substring(0, trimmed.length() - " notNull".length()).trim();
            return context.get(varName) != null;
        }

        // 格式4: "varName startsWith 值"
        if (trimmed.contains(" startsWith ")) {
            String[] parts = trimmed.split(" startsWith ", 2);
            String varValue = context.getAsString(parts[0].trim(), "");
            return varValue.startsWith(parts[1].trim());
        }

        // 默认 false
        return false;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
