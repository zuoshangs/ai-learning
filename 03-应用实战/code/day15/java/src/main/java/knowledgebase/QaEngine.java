package knowledgebase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 问答引擎 - 使用 LLM API 基于检索到的上下文回答问题
 *
 * 通过 OkHttp 调用兼容 OpenAI API 格式的 DeepSeek API，
 * 将检索到的相关文档块作为上下文，生成带引用的回答。
 */
public class QaEngine {

    private final Config config;
    private final okhttp3.OkHttpClient httpClient;

    public QaEngine(Config config) {
        this.config = config;
        this.httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 基于检索到的上下文回答用户问题
     *
     * @param question  用户提问
     * @param contexts  检索到的相关文档块（含引用来源）
     * @return AI 生成的回答
     */
    public String answer(String question, List<VectorStore.SearchResult> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "抱歉，知识库中没有找到与问题相关的信息。";
        }

        try {
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt();

            // 构建上下文内容
            String contextText = buildContext(contexts);

            // 构建用户消息
            String userMessage = buildUserMessage(question, contextText);

            // 调用 API
            String response = callLlmApi(systemPrompt, userMessage);

            // 提取回答内容
            String answer = extractAnswer(response);
            if (answer == null) {
                return "抱歉，AI 模型返回了无效的响应，请稍后重试。";
            }

            // 添加引用来源
            answer = appendCitations(answer, contexts);

            return answer;

        } catch (IOException e) {
            return "调用 AI API 时发生网络错误: " + e.getMessage();
        } catch (Exception e) {
            return "处理问题时发生错误: " + e.getMessage();
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是一个基于个人知识库的智能问答助手。
                
                你的职责是基于提供的知识库内容回答用户的问题。
                
                重要规则：
                1. 只使用提供的知识库内容来回答问题
                2. 如果提供的内容不足以回答问题，明确说明"知识库中没有相关信息"
                3. 回答要简洁、准确、有条理
                4. 引用具体的内容来源（文件名）
                5. 不要编造信息或使用外部知识
                6. 使用中文回答
                """;
    }

    /**
     * 构建上下文文本
     */
    private String buildContext(List<VectorStore.SearchResult> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是知识库中检索到的相关内容（按相关度排序）：\n\n");

        for (int i = 0; i < contexts.size(); i++) {
            VectorStore.SearchResult ctx = contexts.get(i);
            sb.append("--- 文档 ").append(i + 1).append(": ")
                    .append(ctx.getSourceFileName())
                    .append(" (相关度: ").append(String.format("%.2f", ctx.getScore()))
                    .append(") ---\n");
            sb.append(ctx.getText()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(String question, String contextText) {
        return """
                请基于以下知识库内容回答我的问题。
                
                知识库内容：
                %s
                
                我的问题是：%s
                
                请使用中文回答，并在回答中注明信息来源（文件名）。
                """.formatted(contextText, question);
    }

    /**
     * 调用 LLM API（OpenAI 兼容格式）
     */
    private String callLlmApi(String systemPrompt, String userMessage) throws IOException {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", config.getModel());

        JSONArray messages = new JSONArray();

        // 系统消息
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.put(systemMsg);

        // 用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);

        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3); // 低温度，更确定性
        requestBody.put("max_tokens", 2048);

        // 构建 HTTP 请求
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(requestBody.toString(), mediaType);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(config.getChatEndpoint())
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // 执行请求
        System.out.println("  正在调用 AI 模型 (" + config.getModel() + ")...");
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();

            if (statusCode == 401) {
                throw new IOException("API 认证失败（401），请检查 DEEPSEEK_API_KEY 是否正确");
            }
            if (statusCode == 429) {
                throw new IOException("API 请求频率限制（429），请稍后再试");
            }
            if (statusCode == 500 || statusCode == 502 || statusCode == 503) {
                throw new IOException("AI 服务暂时不可用（" + statusCode + "），请稍后再试");
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("API 返回错误 " + statusCode + ": " + responseBody);
            }

            return responseBody;
        }
    }

    /**
     * 从 API 响应中提取回答文本
     */
    private String extractAnswer(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String content = message.optString("content", null);
                if (content != null && !content.isBlank()) {
                    return content.trim();
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("解析 API 响应失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 在回答末尾添加引用来源
     */
    private String appendCitations(String answer, List<VectorStore.SearchResult> contexts) {
        // 提取唯一的来源文件
        List<String> sources = contexts.stream()
                .map(VectorStore.SearchResult::getSourceFileName)
                .distinct()
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            return answer;
        }

        StringBuilder sb = new StringBuilder(answer);
        sb.append("\n\n---\n**参考来源**：\n");
        for (String source : sources) {
            sb.append("- ").append(source).append("\n");
        }

        return sb.toString();
    }
}
