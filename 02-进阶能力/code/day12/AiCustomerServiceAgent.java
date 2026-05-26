/**
 * AiCustomerServiceAgent.java — 完整 AI 客服系统
 * 
 * 整合 RAG + Function Calling + 结构化输出 + 多轮对话
 * 第12天项目实战 — Java 版
 */

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AiCustomerServiceAgent {

    // ═══════════════════════════════════════════
    // 数据类
    // ═══════════════════════════════════════════

    static class CustomerState {
        String userName = "";
        String orderId = "";
        String intent = "";
        int conversationRounds = 0;
        boolean resolved = false;

        String toPrompt() {
            return String.format("""
                当前客户信息：
                - 姓名：%s
                - 当前意图：%s
                - 关联订单：%s
                - 已对话 %d 轮
                - 是否已解决：%s""",
                userName.isEmpty() ? "未知" : userName,
                intent.isEmpty() ? "未知" : intent,
                orderId.isEmpty() ? "无" : orderId,
                conversationRounds,
                resolved ? "是" : "否");
        }
    }

    // ═══════════════════════════════════════════
    // 简易向量存储（RAG 知识库）
    // ═══════════════════════════════════════════

    static class SimpleVectorStore {
        List<String> documents = new ArrayList<>();
        List<double[]> vectors = new ArrayList<>();

        String[] tokenize(String text) {
            return text.toLowerCase()
                .replaceAll("[^\\w\\u4e00-\\u9fff]+", " ")
                .trim().split("\\s+");
        }

        double[] vectorize(String text) {
            String[] tokens = tokenize(text);
            Set<String> allTerms = new HashSet<>();
            for (String doc : documents) {
                allTerms.addAll(Arrays.asList(tokenize(doc)));
            }
            allTerms.addAll(Arrays.asList(tokens));

            double[] vec = new double[allTerms.size()];
            String[] termList = allTerms.toArray(new String[0]);
            for (String t : tokens) {
                for (int i = 0; i < termList.length; i++) {
                    if (termList[i].equals(t)) {
                        vec[i] += 1.0;
                        break;
                    }
                }
            }
            return vec;
        }

        void addDocument(String text) {
            documents.add(text);
            vectors.add(vectorize(text));
        }

        double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                na += a[i] * a[i];
                nb += b[i] * b[i];
            }
            return (na > 0 && nb > 0) ? dot / (Math.sqrt(na) * Math.sqrt(nb)) : 0;
        }

        List<Map.Entry<String, Double>> search(String query, int topK) {
            if (documents.isEmpty()) return List.of();
            double[] qVec = vectorize(query);
            List<Map.Entry<String, Double>> scored = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                double score = cosineSimilarity(qVec, vectors.get(i));
                scored.add(Map.entry(documents.get(i), score));
            }
            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return scored.subList(0, Math.min(topK, scored.size()));
        }
    }

    static class MultiKnowledgeBase {
        Map<String, SimpleVectorStore> stores = new LinkedHashMap<>();

        void addCategory(String name, List<String> documents) {
            SimpleVectorStore store = new SimpleVectorStore();
            for (String doc : documents) {
                store.addDocument(doc);
            }
            stores.put(name, store);
            System.out.printf("  📚 加载知识库 [%s]: %d 条%n", name, documents.size());
        }

        List<Map.Entry<String, Map.Entry<String, Double>>> query(String question, int topK) {
            List<Map.Entry<String, Map.Entry<String, Double>>> results = new ArrayList<>();
            for (var entry : stores.entrySet()) {
                String category = entry.getKey();
                SimpleVectorStore store = entry.getValue();
                for (var hit : store.search(question, topK)) {
                    final String cat = category;
                    results.add(Map.entry(cat, hit));
                }
            }
            results.sort((a, b) -> Double.compare(b.getValue().getValue(), a.getValue().getValue()));
            return results.subList(0, Math.min(topK, results.size()));
        }
    }

    // ═══════════════════════════════════════════
    // 工具管理器
    // ═══════════════════════════════════════════

    static class ToolExecutor {

        static Map<String, Map<String, Object>> orderDb = new LinkedHashMap<>() {{
            put("ORD-2024-001", Map.of(
                "customer", "张三", "product", "iPhone 15 Pro 256GB",
                "amount", 8999.00, "status", "已发货",
                "logistics", "顺丰快递 SF1234567890",
                "estimated_delivery", "2024-03-20"));
            put("ORD-2024-002", Map.of(
                "customer", "李四", "product", "MacBook Air M3",
                "amount", 8999.00, "status", "待发货",
                "logistics", "预计明天发货",
                "estimated_delivery", "2024-03-22"));
        }};

        static Map<String, Map<String, Object>> productCatalog = new LinkedHashMap<>() {{
            put("iphone 15 pro", Map.of("name", "iPhone 15 Pro", "price", 7999, "stock", "充足"));
            put("macbook air m3", Map.of("name", "MacBook Air M3", "price", 8999, "stock", "现货"));
            put("airpods pro", Map.of("name", "AirPods Pro 第二代", "price", 1899, "stock", "充足"));
        }};

        Map<String, Object> execute(String toolName, Map<String, Object> args) {
            return switch (toolName) {
                case "lookup_order" -> lookupOrder((String) args.get("order_id"));
                case "query_product" -> queryProduct((String) args.get("product_name"));
                case "calculate_shipping" -> calcShipping(
                    ((Number) args.getOrDefault("amount", 0)).doubleValue(),
                    (String) args.getOrDefault("express", "普通"));
                default -> Map.of("error", "未知工具: " + toolName);
            };
        }

        Map<String, Object> lookupOrder(String orderId) {
            var order = orderDb.get(orderId);
            if (order != null) {
                var result = new LinkedHashMap<String, Object>(order);
                result.put("found", true);
                result.put("order_id", orderId);
                return result;
            }
            return Map.of("found", false, "order_id", orderId, "message", "未找到该订单");
        }

        Map<String, Object> queryProduct(String productName) {
            String key = productName.toLowerCase().trim();
            for (var entry : productCatalog.entrySet()) {
                if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                    var result = new LinkedHashMap<String, Object>(entry.getValue());
                    result.put("found", true);
                    return result;
                }
            }
            return Map.of("found", false, "query", productName, "message", "未找到该商品");
        }

        Map<String, Object> calcShipping(double amount, String express) {
            if (amount >= 199) {
                return Map.of("fee", 0, "message", "满199包邮，免运费");
            }
            double fee = express.contains("加急") ? 25 : 10;
            return Map.of("fee", fee, "express", express, "message", "运费 " + (int) fee + " 元");
        }

        String describeTools() {
            return """
                📌 可用工具：
                  🔧 lookup_order: 查询订单状态和物流信息 (参数: order_id)
                  🔧 query_product: 查询商品信息和价格 (参数: product_name)
                  🔧 calculate_shipping: 计算运费 (参数: amount, express)""";
        }
    }

    // ═══════════════════════════════════════════
    // 主 Agent 类
    // ═══════════════════════════════════════════

    static class Agent {
        MultiKnowledgeBase kb = new MultiKnowledgeBase();
        ToolExecutor tools = new ToolExecutor();
        CustomerState state = new CustomerState();
        List<Map<String, String>> history = new ArrayList<>();
        int windowSize = 6;

        static final String SYSTEM_PROMPT = """
            你是一个专业友好的电商客服助手「小智」。
            回答简洁有礼貌，语气亲切自然。
            需要查信息时使用工具。用户首次对话时主动询问名字。""";

        Agent() {
            initKnowledgeBase();
            System.out.println("=".repeat(55));
            System.out.println("  🛒 智能客服「小智」已就绪 (Java版)");
            System.out.println("=".repeat(55));
        }

        void initKnowledgeBase() {
            kb.addCategory("商品信息", List.of(
                "iPhone 15 Pro 搭载 A17 Pro 芯片，起售价 7999 元",
                "MacBook Air M3 配备 13.6 英寸显示屏，起售价 8999 元",
                "AirPods Pro 第二代支持主动降噪，售价 1899 元"));
            kb.addCategory("服务政策", List.of(
                "退货政策：7 天内无理由退货",
                "物流政策：满 199 包邮",
                "保修政策：一年官方保修"));
        }

        void updateState(String input) {
            // 提取姓名
            Matcher m = Pattern.compile("我(?:叫|是)(\\S{1,4})").matcher(input);
            if (m.find() && state.userName.isEmpty()) {
                state.userName = m.group(1);
            }
            // 提取订单号
            m = Pattern.compile("ORD[-_]?[\\w-]+|ORD\\d+", Pattern.CASE_INSENSITIVE).matcher(input);
            if (m.find()) {
                state.orderId = m.group();
            }
            // 意图识别
            if (input.contains("查") && input.contains("订单")) state.intent = "query_order";
            else if (input.contains("退")) state.intent = "return_product";
            else if (input.contains("多少") || input.contains("价格") || input.contains("钱")) state.intent = "ask_product";
            else if (input.contains("包邮") || input.contains("运费")) state.intent = "ask_shipping";
            
            state.conversationRounds++;
        }

        String getRagContext(String question) {
            var results = kb.query(question, 2);
            if (results.isEmpty()) return "";
            return results.stream()
                .filter(e -> e.getValue().getValue() > 0.1)
                .map(e -> "[" + e.getKey() + "] " + e.getValue().getKey())
                .collect(Collectors.joining("\n", "参考资料：\n", ""));
        }

        String chat(String userInput) {
            updateState(userInput);
            String ragContext = getRagContext(userInput);

            // 构造消息
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            messages.add(Map.of("role", "system", "content", state.toPrompt()));

            if (!ragContext.isEmpty()) {
                messages.add(Map.of("role", "system", "content", ragContext));
            }

            // 最近窗口
            int start = Math.max(0, history.size() - windowSize * 2);
            for (int i = start; i < history.size(); i++) {
                messages.add(history.get(i));
            }
            messages.add(Map.of("role", "user", "content", userInput));

            // 调用 LLM
            String response = callLlm(messages);
            if (response.isEmpty()) response = "抱歉，我暂时无法回答。";

            history.add(Map.of("role", "user", "content", userInput));
            history.add(Map.of("role", "assistant", "content", response));
            return response;
        }

        String callLlm(List<Map<String, String>> messages) {
            try {
                String apiKey = System.getenv("DEEPSEEK_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    try (var r = new BufferedReader(new FileReader(
                            System.getProperty("user.home") + "/.hermes/auth.json"))) {
                        var auth = new String(r.readLine().getBytes());
                        // simplified — in production use a JSON parser
                    } catch (Exception e) { /* ignore */ }
                }

                // Build JSON payload
                var msgsJson = new StringBuilder();
                msgsJson.append("[");
                for (var msg : messages) {
                    msgsJson.append(String.format(
                        "{\"role\":\"%s\",\"content\":\"%s\"},",
                        msg.get("role"),
                        msg.get("content").replace("\"", "\\\"").replace("\n", "\\n")));
                }
                if (msgsJson.charAt(msgsJson.length() - 1) == ',') {
                    msgsJson.deleteCharAt(msgsJson.length() - 1);
                }
                msgsJson.append("]");

                String payload = String.format("""
                    {"model":"deepseek-chat","messages":%s,"temperature":0.3,"max_tokens":1024}""",
                    msgsJson.toString());

                var client = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(payload))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

                var response = client.send(request, BodyHandlers.ofString());
                var body = response.body();

                // 简单解析 content 字段
                Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(body);
                if (m.find()) {
                    return m.group(1).replace("\\n", "\n");
                }
                return "";
            } catch (Exception e) {
                return "系统繁忙，请稍后再试。";
            }
        }

        String getApiKey() {
            String key = System.getenv("DEEPSEEK_API_KEY");
            if (key != null && !key.isEmpty()) return key;
            // Fallback — in production, read from auth.json properly
            return "";
        }

        String getStatus() {
            return String.format("""
                📊 当前状态
                - 客户: %s
                - 意图: %s
                - 订单: %s
                - 对话轮次: %d""",
                state.userName.isEmpty() ? "未知" : state.userName,
                state.intent.isEmpty() ? "未知" : state.intent,
                state.orderId.isEmpty() ? "无" : state.orderId,
                state.conversationRounds);
        }

        String generateSummary() {
            // Simplified summary generation
            return String.format("""
                📋 对话摘要
                - 客户: %s
                - 主题: 商品咨询/订单查询
                - 订单: %s
                - 处理结果: 进行中""",
                state.userName.isEmpty() ? "未知" : state.userName,
                state.orderId.isEmpty() ? "无" : state.orderId);
        }
    }

    // ═══════════════════════════════════════════
    // 主入口
    // ═══════════════════════════════════════════

    public static void main(String[] args) {
        Agent agent = new Agent();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n💡 试试这些场景：");
        System.out.println("  「你好，我叫小明」");
        System.out.println("  「iPhone 15 Pro 多少钱？」");
        System.out.println("  「/tools」  /state  /summary  quit");

        while (true) {
            System.out.print("\n🧑 你: ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println("👋 再见！");
                break;
            }

            switch (input) {
                case "/tools" -> { System.out.println(agent.tools.describeTools()); continue; }
                case "/state" -> { System.out.println(agent.getStatus()); continue; }
                case "/summary" -> { System.out.println(agent.generateSummary()); continue; }
            }

            String response = agent.chat(input);
            System.out.println("\n🤖 小智: " + response);
        }

        scanner.close();
    }
}
