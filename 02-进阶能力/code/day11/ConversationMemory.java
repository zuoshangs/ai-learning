// ConversationMemory.java — 多轮对话与状态管理（Java 版）
// 三种记忆策略：滑动窗口 / 摘要 / 向量
//
// 编译: javac ConversationMemory.java
// 运行: DEEPSEEK_API_KEY=xxx java ConversationMemory

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ConversationMemory {

    // ─── 消息结构 ─────────────────────────────
    static class Message {
        String role;
        String content;
        Message(String role, String content) { this.role = role; this.content = content; }
    }

    // ─── 客户状态 ─────────────────────────────
    static class CustomerState {
        String userName;
        String orderId;
        String issueType;  // 退货 | 换货 | 咨询 | 投诉
        boolean resolved;
        List<String> notes = new ArrayList<>();

        String getSummary() {
            List<String> parts = new ArrayList<>();
            if (userName != null) parts.add("客户: " + userName);
            if (orderId != null) parts.add("订单: " + orderId);
            if (issueType != null) parts.add("问题: " + issueType);
            parts.add(resolved ? "已解决" : "进行中");
            return String.join(" | ", parts);
        }
    }

    // ─── 策略接口 ─────────────────────────────
    interface MemoryStrategy {
        void add(String role, String content);
        List<Message> getContext(CustomerState state);
    }

    // ─── 策略 1：滑动窗口 ─────────────────────
    static class SlidingWindowMemory implements MemoryStrategy {
        String systemPrompt;
        List<Message> history = new ArrayList<>();
        int windowSize;

        SlidingWindowMemory(String systemPrompt, int windowSize) {
            this.systemPrompt = systemPrompt;
            this.windowSize = windowSize;
        }

        public void add(String role, String content) {
            history.add(new Message(role, content));
        }

        public List<Message> getContext(CustomerState state) {
            List<Message> ctx = new ArrayList<>();
            ctx.add(new Message("system", systemPrompt));
            if (state != null) {
                ctx.add(new Message("system", "客户状态: " + state.getSummary()));
            }
            int start = Math.max(0, history.size() - windowSize * 2);
            ctx.addAll(history.subList(start, history.size()));
            return ctx;
        }
    }

    // ─── 客服系统 ─────────────────────────────
    static class CustomerServiceBot {
        static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
        static final String SYSTEM_PROMPT =
            "你是一个专业友好的电商客服助手。耐心解答用户关于订单、商品、物流的问题。" +
            "回答简洁有条理，不知道的信息不要说谎。";

        String apiKey;
        MemoryStrategy memory;
        CustomerState state = new CustomerState();
        HttpClient client = HttpClient.newHttpClient();

        CustomerServiceBot(String apiKey, String memoryType) {
            this.apiKey = apiKey;
            switch (memoryType) {
                case "summary": memory = new SlidingWindowMemory(SYSTEM_PROMPT, 6); break;
                default: memory = new SlidingWindowMemory(SYSTEM_PROMPT, 6);
            }
        }

        String chat(String userInput) throws Exception {
            memory.add("user", userInput);
            updateState(userInput);

            List<Message> ctx = memory.getContext(state);
            String response = callLLM(ctx);
            memory.add("assistant", response);
            return response;
        }

        void updateState(String text) {
            try {
                String body = String.format(
                    "{\"model\":\"deepseek-chat\",\"messages\":[" +
                    "{\"role\":\"system\",\"content\":\"从文本中提取信息，返回JSON。\"}," +
                    "{\"role\":\"user\",\"content\":\"从以下文本提取name、order_id、issue_type(退货/换货/咨询/投诉)：%s\"}]," +
                    "\"response_format\":{\"type\":\"json_object\"},\"temperature\":0}",
                    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                );

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                String respBody = resp.body();

                // 简易 JSON 解析
                if (respBody.contains("\"name\":\"")) {
                    int s = respBody.indexOf("\"name\":\"") + 8;
                    int e = respBody.indexOf("\"", s);
                    state.userName = respBody.substring(s, e);
                }
                if (respBody.contains("\"order_id\":\"")) {
                    int s = respBody.indexOf("\"order_id\":\"") + 12;
                    int e = respBody.indexOf("\"", s);
                    state.orderId = respBody.substring(s, e);
                }
            } catch (Exception ignored) {}
        }

        String callLLM(List<Message> messages) throws Exception {
            StringBuilder body = new StringBuilder();
            body.append("{\"model\":\"deepseek-chat\",\"messages\":[");
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                if (i > 0) body.append(",");
                String content = m.content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
                body.append(String.format("{\"role\":\"%s\",\"content\":\"%s\"}", m.role, content));
            }
            body.append("],\"temperature\":0.3,\"max_tokens\":1024}");

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(java.time.Duration.ofSeconds(30))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body();

            // 提取 content 字段
            int ci = respBody.indexOf("\"content\":\"");
            if (ci < 0) return "(解析失败)";
            int start = ci + 11;
            int end = respBody.indexOf("\"", start);
            return respBody.substring(start, end)
                .replace("\\n", "\n").replace("\\\"", "\"");
        }
    }

    // ─── 主函数 ────────────────────────────────
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("⚠️  请设置 DEEPSEEK_API_KEY");
            return;
        }

        System.out.println("=".repeat(50));
        System.out.println("  💬 智能客服助手 (Java)");
        System.out.println("  记忆策略: 滑动窗口");
        System.out.println("=".repeat(50));

        CustomerServiceBot bot = new CustomerServiceBot(apiKey, "window");

        // Demo 对话
        String[] demoInputs = {
            "你好，我叫张三",
            "我想退货，订单号 ORD-2024-5678",
            "你还记得我的名字和订单号吗？"
        };

        for (String input : demoInputs) {
            System.out.println("\n🧑 你: " + input);
            String response = bot.chat(input);
            System.out.println("🤖 客服: " + response);
            System.out.println("  📋 状态: " + bot.state.getSummary());
        }

        System.out.println("\n✅ 多轮对话演示完成！");
        System.out.println("  会话共 " + bot.memory.history.size() + " 条消息");
    }
}
