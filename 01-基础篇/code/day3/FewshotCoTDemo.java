package ai.learning.day3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * Few-shot CoT + Self-Consistency 演示（Java版）
 *
 * 编译与运行方式同上。
 */
public class FewshotCoTDemo {

    static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    static final String API_URL = "https://api.deepseek.com/chat/completions";
    static final HttpClient client = HttpClient.newHttpClient();

    static String ask(String prompt, double temp) throws Exception {
        String body = String.format("""
            {
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": "%s"}],
                "temperature": %.1f,
                "max_tokens": 1024
            }
            """, CotDemo.escapeJson(prompt), temp);

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
        // Few-shot CoT
        System.out.println("=".repeat(60));
        System.out.println("实验：Few-shot CoT");
        System.out.println("=".repeat(60));

        String prompt = """
            参考下面的示例，用同样的分步推理方式回答问题：

            示例问题：一个正方形周长20厘米，面积是多少？
            示例推理：
            1. 正方形周长 = 4 × 边长
            2. 边长 = 20 ÷ 4 = 5厘米
            3. 面积 = 边长 × 边长 = 5 × 5 = 25平方厘米
            答案：25平方厘米

            示例问题：小明有10个苹果，给了小红3个，又买了5个，现在有几个？
            示例推理：
            1. 初始有10个
            2. 给小红3个：10 - 3 = 7个
            3. 又买5个：7 + 5 = 12个
            答案：12个

            现在请回答：
            鸡兔同笼，头35个，脚94只。鸡和兔各有多少只？
            让我们一步一步思考。
            """;

        String r1 = ask(prompt, 0);
        System.out.println("回答：\n" + r1 + "\n");
    }
}
