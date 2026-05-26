// SimpleVectorStore.java — 从零搭建的向量检索器（Java 版）
// 编译: javac SimpleVectorStore.java
// 运行: java SimpleVectorStore

import java.util.*;
import java.util.stream.Collectors;

public class SimpleVectorStore {

    // ─── 数据结构 ─────────────────────────────
    static class VectorRecord {
        double[] vector;
        String text;
        Map<String, String> metadata;

        VectorRecord(double[] vector, String text, Map<String, String> metadata) {
            this.vector = vector;
            this.text = text;
            this.metadata = metadata;
        }
    }

    static class SearchResult {
        double score;
        String text;
        Map<String, String> metadata;

        SearchResult(double score, String text, Map<String, String> metadata) {
            this.score = score;
            this.text = text;
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return String.format("[%.4f] %s (%s)", score, text, metadata);
        }
    }

    // ─── 向量存储 ─────────────────────────────
    private final List<VectorRecord> records = new ArrayList<>();

    public void add(double[] vector, String text, Map<String, String> metadata) {
        records.add(new VectorRecord(vector, text, metadata));
    }

    // ─── 余弦相似度 ───────────────────────────
    public double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0 || normB == 0) return 0;
        return dot / (normA * normB);
    }

    // ─── 向量搜索 ─────────────────────────────
    public List<SearchResult> search(double[] queryVector, int topK) {
        List<SearchResult> results = new ArrayList<>();
        for (VectorRecord r : records) {
            double score = cosineSimilarity(queryVector, r.vector);
            results.add(new SearchResult(score, r.text, r.metadata));
        }
        // 按相似度降序排列
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results.subList(0, Math.min(topK, results.size()));
    }

    public int size() {
        return records.size();
    }

    // ─── 测试 ────────────────────────────────────
    public static void main(String[] args) {
        SimpleVectorStore store = new SimpleVectorStore();

        // 插入测试数据
        store.add(
            new double[]{0.9, 0.1, 0.0},
            "猫是哺乳动物，喜欢吃鱼",
            Map.of("topic", "动物")
        );
        store.add(
            new double[]{0.8, 0.2, 0.1},
            "狗是哺乳动物，喜欢出去玩",
            Map.of("topic", "动物")
        );
        store.add(
            new double[]{0.1, 0.3, 0.9},
            "Python 是一种编程语言",
            Map.of("topic", "编程")
        );
        store.add(
            new double[]{0.0, 0.2, 0.8},
            "Java 也是一种编程语言",
            Map.of("topic", "编程")
        );

        // 搜索"猫"
        double[] query = {0.85, 0.15, 0.05};
        List<SearchResult> results = store.search(query, 2);

        System.out.println("🔍 搜索 '猫' 的结果：");
        for (SearchResult r : results) {
            System.out.println("  " + r);
        }

        System.out.println("\n共 " + store.size() + " 条记录 ✅");
    }
}
