package ai.learning.day3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * Tree-of-Thought（思维树）演示（Java版）
 * 三步法：探索 → 评估 → 选择
 */
public class ToTDemo {

    static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    static final String API_URL = "https://api.deepseek.com/chat/completions";
    static final HttpClient client = HttpClient.newHttpClient();

    static String ask(String prompt, String system) throws Exception {
        String body = String.format("""
            {
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                ],
                "temperature": 0.7,
                "max_tokens": 1024
            }
            """, CotDemo.escapeJson(system), CotDemo.escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return CotDemo.extractContent(response.body());
    }

    public static void main(String[] args) throws Exception {
        String system = "你是一个逻辑推理专家。";

        System.out.println("=".repeat(60));
        System.out.println("Tree-of-Thought（思维树）演示");
        System.out.println("问题：24点游戏 — 用 3, 3, 8, 8 算出24");
        System.out.println("=".repeat(60));

        // 第一步：探索
        System.out.println("\n【第1步：探索 — 列出多种思路】\n");
        String step1 = ask(
            "用 3, 3, 8, 8 四个数字，通过加减乘除和括号，算出24。"
            + "请列出3-4种不同的解题思路，不需要给出完整计算，只描述策略方向。",
            system
        );
        System.out.println(step1);

        // 第二步：评估
        System.out.println("\n【第2步：评估 — 判断每种思路可行性】\n");
        String step2 = ask(
            "以下是用 3,3,8,8 算24的几种思路：\n" + step1 + "\n\n"
            + "请客观评估每种思路的可行性，指出可能的陷阱。",
            system
        );
        System.out.println(step2);

        // 第三步：选择
        System.out.println("\n【第3步：选择 — 选出最佳方案并完整推导】\n");
        String step3 = ask(
            "基于以上分析，选择最佳的解题思路，给出完整的计算过程。\n"
            + "题目：用 3, 3, 8, 8 算出24。",
            system
        );
        System.out.println(step3);
    }
}
