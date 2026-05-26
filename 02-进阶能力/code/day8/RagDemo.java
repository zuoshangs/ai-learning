// RagDemo.java — 完整 RAG 问答系统（Java 版）
// 三段论：检索 → 增强 → 生成
//
// 依赖: 需要 org.json 库（Maven: implementation 'org.json:json:20231013'）
// 编译: javac -cp .:org.json-20231013.jar RagDemo.java
// 运行: DEEPSEEK_API_KEY=xxx java -cp .:org.json-20231013.jar RagDemo
//
// 注意: Java 版使用 DeepSeek Embedding API 生成向量
//       （需要 DeepSeek 支持 embedding 接口）

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class RagDemo {

    // ─── 配置 ─────────────────────────────────
    static final String DEEPSEEK_API_KEY = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "");
    static final String DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions";
    static final String EMBEDDING_URL = "https://api.deepseek.com/v1/embeddings";
    static final int CHUNK_SIZE = 500;
    static final int CHUNK_OVERLAP = 50;

    // ─── 数据结构（复用 SimpleVectorStore）─────
    static class SearchResult {
        double score;
        String text;
        Map<String, String> metadata;
        SearchResult(double score, String text, Map<String, String> metadata) {
            this.score = score; this.text = text; this.metadata = metadata;
        }
    }

    static class SimpleVectorStore {
        List<double[]> vectors = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Map<String, String>> metadatas = new ArrayList<>();

        void add(double[] vector, String text, Map<String, String> metadata) {
            vectors.add(vector); texts.add(text); metadatas.add(metadata);
        }

        double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            normA = Math.sqrt(normA); normB = Math.sqrt(normB);
            return (normA == 0 || normB == 0) ? 0 : dot / (normA * normB);
        }

        List<SearchResult> search(double[] queryVec, int topK) {
            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < vectors.size(); i++) {
                double score = cosineSimilarity(queryVec, vectors.get(i));
                results.add(new SearchResult(score, texts.get(i), metadatas.get(i)));
            }
            results.sort((a, b) -> Double.compare(b.score, a.score));
            return results.subList(0, Math.min(topK, results.size()));
        }

        int size() { return texts.size(); }
    }

    // ─── 文本分割 ─────────────────────────────
    static List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            if (current.length() + para.length() < CHUNK_SIZE) {
                current.append(para).append("\n\n");
            } else {
                if (current.toString().trim().length() > 0) {
                    chunks.add(current.toString().trim());
                }
                current = new StringBuilder(para).append("\n\n");
            }
        }
        if (current.toString().trim().length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    // ─── Embedding ────────────────────────────
    static double[] getEmbedding(String text) throws Exception {
        // 简易实现：调用 DeepSeek Embedding API
        // 如果 DeepSeek 不支持 embedding，可替换为其他服务
        HttpClient client = HttpClient.newHttpClient();
        String jsonBody = String.format(
            "{\"model\":\"text-embedding-ada-002\",\"input\":[\"%s\"]}",
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        );

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(EMBEDDING_URL))
            .header("Authorization", "Bearer " + DEEPSEEK_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(java.time.Duration.ofSeconds(30))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();

        // 简易 JSON 解析（生产环境请用 Jackson/Gson）
        int start = body.indexOf("\"embedding\":[") + 12;
        int end = body.indexOf("]", start);
        String[] parts = body.substring(start, end).split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Double.parseDouble(parts[i].trim());
        }
        return vec;
    }

    // ─── RAG 查询 ─────────────────────────────
    static String ragQuery(String question, SimpleVectorStore store, int topK) throws Exception {
        // ① 检索
        double[] queryVec = getEmbedding(question);
        List<SearchResult> results = store.search(queryVec, topK);

        // ② 增强
        String context = results.stream()
            .map(r -> r.text)
            .collect(Collectors.joining("\n\n"));

        String prompt = String.format(
            "你是一个专业的文档问答助手。请根据以下资料回答用户的问题。\n\n" +
            "资料：\n%s\n\n问题：%s\n\n" +
            "要求：\n- 如果资料中有相关信息，请基于资料回答\n" +
            "- 如果资料中没有相关信息，请明确说'资料中没有提到'\n" +
            "- 不要编造信息\n- 用中文回答",
            context, question
        );

        // ③ 生成
        HttpClient client = HttpClient.newHttpClient();
        String jsonBody = String.format(
            "{\"model\":\"deepseek-chat\",\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"你是一个文档问答助手，基于用户提供的资料回答问题。\"}," +
            "{\"role\":\"user\",\"content\":\"%s\"}]," +
            "\"temperature\":0.3,\"max_tokens\":1024}",
            prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        );

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(DEEPSEEK_URL))
            .header("Authorization", "Bearer " + DEEPSEEK_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(java.time.Duration.ofSeconds(30))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();

        // 简易 JSON 解析
        int start = body.indexOf("\"content\":\"") + 11;
        int end = body.indexOf("\"", start);
        return body.substring(start, end)
            .replace("\\n", "\n")
            .replace("\\\"", "\"");
    }

    // ─── 主函数 ────────────────────────────────
    public static void main(String[] args) throws Exception {
        if (DEEPSEEK_API_KEY.isEmpty()) {
            System.out.println("⚠️  请设置环境变量 DEEPSEEK_API_KEY");
            System.out.println("   export DEEPSEEK_API_KEY=your_key_here");
            System.exit(1);
        }

        System.out.println("=".repeat(55));
        System.out.println("  🔍 RAG 文档问答系统 (Java)");
        System.out.println("  三段论：检索 → 增强 → 生成");
        System.out.println("=".repeat(55));

        // 初始化
        SimpleVectorStore store = new SimpleVectorStore();

        // 读取 data 目录
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
            System.out.println("\n📁 已创建 data/ 目录，请放入 .txt 文件后重新运行");
            return;
        }

        List<Path> files = Files.list(dataDir)
            .filter(p -> p.toString().endsWith(".txt"))
            .collect(Collectors.toList());

        if (files.isEmpty()) {
            System.out.println("\n📁 data/ 目录为空，请放入 .txt 文件");
            return;
        }

        // 构建知识库
        System.out.println("\n📚 构建知识库...");
        for (Path file : files) {
            System.out.println("\n  📖 处理: " + file.getFileName());
            String text = Files.readString(file, StandardCharsets.UTF_8);
            List<String> chunks = chunkText(text);
            System.out.println("  📄 已分割为 " + chunks.size() + " 个片段");

            for (int i = 0; i < chunks.size(); i++) {
                System.out.println("     ⏳ 生成向量 (" + (i + 1) + "/" + chunks.size() + ")...");
                double[] vec = getEmbedding(chunks.get(i));
                store.add(vec, chunks.get(i), Map.of(
                    "source", file.getFileName().toString(),
                    "chunk", String.valueOf(i)
                ));
            }
        }

        System.out.println("\n✅ 知识库构建完成（共 " + store.size() + " 个片段）");

        // 问答循环
        System.out.println("\n💬 输入问题（输入 quit 退出）");
        try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
            while (true) {
                System.out.print("\n❓ 你: ");
                String question = scanner.nextLine().trim();
                if (question.isEmpty()) continue;
                if (question.equalsIgnoreCase("quit") || question.equalsIgnoreCase("q")) {
                    System.out.println("👋 再见！");
                    break;
                }

                System.out.println("   🔍 检索中...");
                String answer = ragQuery(question, store, 3);
                System.out.println("\n📝 回答:\n" + answer);
            }
        }
    }
}
