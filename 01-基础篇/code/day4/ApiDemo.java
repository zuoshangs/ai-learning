package ai.learning.day4;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * 第4天：API 基础调用演示
 *
 * 运行前设置环境变量：
 * export DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY ~/.hermes/.env | cut -d= -f2 | tr -d '"')
 *
 * 编译：javac ApiDemo.java
 * 运行：java ai.learning.day4.ApiDemo
 */
public class ApiDemo {

    static final HttpClient client = HttpClient.newHttpClient();

    static String callApi(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/chat/completions"))
            .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    static String extractContent(String json) {
        int idx = json.indexOf("\"content\":\"");
        if (idx < 0) return "解析失败";
        int start = idx + 11;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    if (n == 'n') { sb.append('\n'); i++; }
                    else if (n == '"') { sb.append('"'); i++; }
                    else if (n == '\\') { sb.append('\\'); i++; }
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

    static void printUsage(String json) {
        int pt = json.indexOf("\"prompt_tokens\":");
        int ct = json.indexOf("\"completion_tokens\":");
        int tt = json.indexOf("\"total_tokens\":");
        if (pt > 0) System.out.println("  输入: " + extractNum(json, pt));
        if (ct > 0) System.out.println("  输出: " + extractNum(json, ct));
        if (tt > 0) System.out.println("  总计: " + extractNum(json, tt));
    }

    static String extractNum(String json, int start) {
        int i = json.indexOf(':', start) + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && Character.isDigit(json.charAt(i))) {
            sb.append(json.charAt(i++));
        }
        return sb.toString();
    }

    static String makeBody(String userMsg, double temp, int maxTokens) {
        return String.format("""
            {
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": "%s"}],
                "temperature": %.1f,
                "max_tokens": %d
            }
            """, escapeJson(userMsg), temp, maxTokens);
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    public static void main(String[] args) throws Exception {
        // 实验1
        System.out.println("=".repeat(60));
        System.out.println("实验1：Token 用量");
        System.out.println("=".repeat(60));

        String body1 = makeBody("请用20字以内介绍 Python。", 0.7, 200);
        String resp1 = callApi(body1);
        System.out.println("回答: " + extractContent(resp1));
        printUsage(resp1);

        // 实验2
        System.out.println("\n" + "=".repeat(60));
        System.out.println("实验2：Temperature 对比");
        System.out.println("=".repeat(60));

        String cold = callApi(makeBody("说一个数字（只输出数字）：", 0, 50));
        String hot = callApi(makeBody("说一个数字（只输出数字）：", 1.5, 50));
        System.out.println("Temperature=0:   " + extractContent(cold));
        System.out.println("Temperature=1.5: " + extractContent(hot));
    }
}
