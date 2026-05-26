// StructuredOutputDemo.java — 三种结构化输出方法对比（Java 版）
// 方法1: Prompt 约束 | 方法2: JSON Mode | 方法3: Function Calling
//
// 依赖: org.json (Maven: implementation 'org.json:json:20231013')
// 编译: javac -cp .:org.json-20231013.jar StructuredOutputDemo.java
// 运行: DEEPSEEK_API_KEY=xxx java -cp .:org.json-20231013.jar StructuredOutputDemo

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StructuredOutputDemo {

    static final String API_KEY = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "");
    static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    static HttpClient client = HttpClient.newHttpClient();

    // ── 调用 API ──
    static String callLLM(String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(java.time.Duration.ofSeconds(30))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    static String extractContent(String response) {
        // 简易 JSON 解析：提取 content 字段
        int ci = response.indexOf("\"content\":\"");
        if (ci < 0) return null;
        int start = ci + 11;
        int end = response.indexOf("\"", start);
        return response.substring(start, end)
            .replace("\\n", "\n").replace("\\\"", "\"");
    }

    static String extractToolCallArgs(String response) {
        // 简易 JSON 解析：提取 tool_calls 中的 arguments
        int ai = response.indexOf("\"arguments\":\"");
        if (ai < 0) {
            // 有的 API 返回未转义的 JSON
            ai = response.indexOf("\"arguments\": ");
            if (ai < 0) return null;
            int start = response.indexOf("{", ai);
            int end = response.lastIndexOf("}") + 1;
            return response.substring(start, end);
        }
        int start = ai + 13;
        int end = response.indexOf("\"", start);
        return response.substring(start, end)
            .replace("\\n", "\n").replace("\\\"", "\"");
    }

    // ── 方法1: Prompt 约束 ──
    static String method1Prompt(String text) throws Exception {
        String prompt = "从以下文本中提取信息，只返回JSON（不要加任何其他文字）：\n\n"
            + "文本：" + text + "\n\n"
            + "要求的JSON格式：\n"
            + "{\"name\": \"姓名\", \"age\": \"年龄（数字）\", \"job\": \"职业\"}";

        String body = String.format(
            "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0}",
            prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        );
        String resp = callLLM(body);
        String content = extractContent(resp);
        if (content == null) return "解析失败";

        // 清理 markdown 包裹
        if (content.contains("```")) {
            String[] parts = content.split("```");
            for (String p : parts) {
                if (p.contains("{") && p.contains("}")) {
                    content = p;
                    break;
                }
            }
        }
        // 提取 JSON
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}") + 1;
        return (start >= 0) ? content.substring(start, end) : "未找到 JSON";
    }

    // ── 方法2: JSON Mode ──
    static String method2JsonMode(String text) throws Exception {
        String body = String.format(
            "{\"model\":\"deepseek-chat\",\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"你是一个信息提取助手。请从用户输入中提取信息，返回JSON格式。\"}," +
            "{\"role\":\"user\",\"content\":\"从以下文本提取姓名、年龄、职业：%s\"}]," +
            "\"response_format\":{\"type\":\"json_object\"},\"temperature\":0}",
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        );
        String resp = callLLM(body);
        String content = extractContent(resp);
        return (content != null) ? content : "解析失败";
    }

    // ── 方法3: Function Calling ──
    static String method3FunctionCalling(String text) throws Exception {
        String body = String.format(
            "{\"model\":\"deepseek-chat\",\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"从用户输入中提取结构化信息。\"}," +
            "{\"role\":\"user\",\"content\":\"%s\"}]," +
            "\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"extract_person_info\"," +
            "\"description\":\"从文本中提取人物信息\"," +
            "\"parameters\":{\"type\":\"object\",\"properties\":{" +
            "\"name\":{\"type\":\"string\",\"description\":\"姓名\"}," +
            "\"age\":{\"type\":\"integer\",\"description\":\"年龄\"}," +
            "\"job\":{\"type\":\"string\",\"description\":\"职业\"}," +
            "\"city\":{\"type\":\"string\",\"description\":\"所在城市\"}" +
            "},\"required\":[\"name\",\"age\",\"job\"]}}}]," +
            "\"tool_choice\":{\"type\":\"function\",\"function\":{\"name\":\"extract_person_info\"}}," +
            "\"temperature\":0}",
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        );
        String resp = callLLM(body);
        String args = extractToolCallArgs(resp);
        return (args != null) ? args : "解析失败";
    }

    // ── 主函数 ──
    public static void main(String[] args) throws Exception {
        if (API_KEY.isEmpty()) {
            System.out.println("⚠️  请设置 DEEPSEEK_API_KEY");
            return;
        }

        String[] tests = {
            "我叫张三，28岁，软件工程师",
            "李四，35岁，北京，产品经理"
        };

        for (String text : tests) {
            System.out.println("=".repeat(55));
            System.out.println("📄 输入: " + text);
            System.out.println("=".repeat(55));

            System.out.println("\n📝 Prompt 约束:");
            System.out.println("  " + method1Prompt(text));

            System.out.println("\n🔧 JSON Mode:");
            System.out.println("  " + method2JsonMode(text));

            System.out.println("\n⚡ Function Calling:");
            System.out.println("  " + method3FunctionCalling(text));
            System.out.println();
        }
    }
}
