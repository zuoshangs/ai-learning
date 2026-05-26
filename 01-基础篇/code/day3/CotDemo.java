package ai.learning.day3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * CoT思维链演示 - Zero-shot vs Zero-shot CoT对比
 *
 * 编译：javac -cp "gson-2.10.1.jar" CotDemo.java
 * 运行：java -cp ".:gson-2.10.1.jar" ai.learning.day3.CotDemo
 */
public class CotDemo {

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
                "temperature": 0,
                "max_tokens": 1024
            }
            """, escapeJson(system), escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 简单解析 JSON 提取 content 字段
        String respBody = response.body();
        return extractContent(respBody);
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String extractContent(String json) {
        // 简单解析：找到 "content":"..." 或 "content":"...\n..."
        int idx = json.indexOf("\"content\":\"");
        if (idx < 0) return "解析失败";
        int start = idx + 11; // len of "content":""
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                // 处理转义字符
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == 'n') { sb.append('\n'); i++; }
                    else if (next == 't') { sb.append('\t'); i++; }
                    else if (next == 'r') { sb.append('\r'); i++; }
                    else if (next == '"') { sb.append('"'); i++; }
                    else if (next == '\\') { sb.append('\\'); i++; }
                    else { sb.append(c); }
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        String system = "你是一个数学老师，擅长分步推理。";
        String q1 = "一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少平方厘米？";

        // 实验1：Zero-shot
        System.out.println("=".repeat(60));
        System.out.println("实验1：Zero-shot（直接问）");
        System.out.println("=".repeat(60));
        System.out.println("问题：" + q1 + "\n");
        String r1 = ask(q1, system);
        System.out.println("回答：\n" + r1 + "\n");

        // 实验2：Zero-shot CoT
        System.out.println("=".repeat(60));
        System.out.println("实验2：Zero-shot CoT（让我们一步一步思考）");
        System.out.println("=".repeat(60));
        String q2 = q1 + "\n\n让我们一步一步思考。";
        String r2 = ask(q2, system);
        System.out.println("回答：\n" + r2 + "\n");
    }
}
