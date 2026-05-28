package com.ai.cs.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Knowledge base service with TF-IDF vectorization + cosine similarity search.
 * Supports:
 * - Document CRUD (create, read, update, delete)
 * - Semantic search via TF-IDF / Ollama embeddings
 * - Category-based filtering
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, double[]> vectors = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    @Value("${app.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.model:qwen2.5:0.5b}")
    private String ollamaModel;

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
            "没有", "看", "好", "自己", "这", "他", "她", "它", "们",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "and", "but", "or", "if", "because", "what", "which", "who", "whom",
            "this", "that", "these", "those", "then", "than", "too", "very");

    public KnowledgeBaseService(HttpClient httpClient) {
        this.httpClient = httpClient;
        // Load seed FAQ documents
        seedDocuments();
    }

    public Document addDocument(String title, String content, String category) {
        String id = UUID.randomUUID().toString();
        Document doc = new Document(id, title, content, category);
        documents.put(id, doc);

        // Compute TF-IDF vector
        double[] vector = computeTfidfVector(content);
        vectors.put(id, vector);

        log.info("Added document: {} (category={})", title, category);
        return doc;
    }

    public boolean deleteDocument(String id) {
        documents.remove(id);
        vectors.remove(id);
        return true;
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values()).stream()
                .sorted(Comparator.comparingLong(Document::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Document> getDocumentsByCategory(String category) {
        return documents.values().stream()
                .filter(d -> category.equals(d.getCategory()))
                .sorted(Comparator.comparingLong(Document::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ========================================
    // Semantic Search
    // ========================================

    /**
     * Search knowledge base by semantic similarity.
     */
    public List<SearchResult> search(String query, int topK) {
        double[] queryVec = computeTfidfVector(query);

        return documents.keySet().stream()
                .map(id -> {
                    Document doc = documents.get(id);
                    double[] docVec = vectors.get(id);
                    double score = cosineSimilarity(queryVec, docVec);
                    return new SearchResult(doc, score);
                })
                .filter(r -> r.score > 0.05)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Search with category filter.
     */
    public List<SearchResult> search(String query, String category, int topK) {
        double[] queryVec = computeTfidfVector(query);

        return documents.values().stream()
                .filter(d -> category == null || category.equals(d.getCategory()))
                .map(doc -> {
                    double[] docVec = vectors.get(doc.getId());
                    double score = cosineSimilarity(queryVec, docVec);
                    return new SearchResult(doc, score);
                })
                .filter(r -> r.score > 0.05)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Build RAG context from search results.
     * Returns a formatted string with relevant knowledge.
     */
    public String buildRagContext(String query, int topK) {
        List<SearchResult> results = search(query, topK);
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n## 相关知识库\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("\n[").append(i + 1).append("] ").append(r.document.getTitle());
            sb.append(" (").append(String.format("%.0f%%", r.score * 100)).append(" 相似)\n");
            sb.append(r.document.getContent()).append("\n");
        }
        return sb.toString();
    }

    public int documentCount() {
        return documents.size();
    }

    public Set<String> getCategories() {
        return documents.values().stream()
                .map(Document::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // ========================================
    // TF-IDF Vectorization
    // ========================================

    Map<String, Double> computeTf(String text) {
        String[] tokens = text.toLowerCase()
                .replaceAll("[\\p{P}\\p{S}]", " ")
                .split("\\s+");

        Map<String, Double> tf = new HashMap<>();
        for (String token : tokens) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) continue;
            // Simple Chinese character bigram for Chinese text
            for (int i = 0; i < token.length() - 1; i++) {
                String bigram = token.substring(i, i + 2);
                if (bigram.matches("[\\u4e00-\\u9fff]{2}")) {
                    tf.merge(bigram, 1.0, Double::sum);
                }
            }
            // English words
            if (token.matches("[a-z]+")) {
                tf.merge(token, 1.0, Double::sum);
            }
        }

        double total = tf.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    private double[] computeTfidfVector(String text) {
        Map<String, Double> tf = computeTf(text);
        double[] vec = new double[tf.size()];
        int i = 0;
        for (double val : tf.values()) {
            vec[i++] = val;
        }
        return vec;
    }

    double cosineSimilarity(double[] a, double[] b) {
        int len = Math.max(a.length, b.length);
        double[] va = Arrays.copyOf(a, len);
        double[] vb = Arrays.copyOf(b, len);

        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < len; i++) {
            dot += va[i] * vb[i];
            na += va[i] * va[i];
            nb += vb[i] * vb[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    // ========================================
    // Ollama Embeddings (for future PgVector)
    // ========================================

    public double[] getOllamaEmbedding(String text) {
        try {
            String body = String.format("""
                    {"model": "%s", "prompt": "%s"}
                    """, ollamaModel, text.replace("\"", "\\\"").replace("\n", "\\n"));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                // Parse embedding array from response
                String body2 = resp.body();
                int idx = body2.indexOf("\"embedding\":[");
                if (idx > 0) {
                    idx += 12;
                    int end = body2.indexOf("]", idx);
                    String[] parts = body2.substring(idx, end).split(",");
                    double[] embedding = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        embedding[i] = Double.parseDouble(parts[i].trim());
                    }
                    return embedding;
                }
            }
        } catch (Exception e) {
            log.warn("Ollama embedding failed: {}", e.getMessage());
        }
        // Fallback to TF-IDF
        return computeTfidfVector(text);
    }

    // ========================================
    // Seed Data
    // ========================================

    private void seedDocuments() {
        addDocument("退货政策",
                "本平台支持30天内无理由退货。商品需保持原状、配件齐全、包装完整。特殊商品（如食品、内衣）除外。",
                "售后");

        addDocument("退款流程",
                "退款将在我们收到退货后的5-7个工作日内原路返回。使用信用卡支付的退款需要7-10个工作日。退款金额包含商品原价和税费。",
                "售后");

        addDocument("物流配送",
                "标准配送3-5个工作日，满99元包邮。加急配送1-2个工作日，需加收15元。当日达仅限部分城市，需在11:00前下单。",
                "物流");

        addDocument("会员等级",
                "会员分为普通、银卡、金卡、钻石卡四级。银卡需年消费1000元（9.5折），金卡3000元（9折），钻石卡10000元（8.5折）。",
                "会员");

        addDocument("联系人工客服",
                "您可以拨打客服热线 400-123-4567（工作日9:00-21:00），或发送邮件至 support@example.com。在线客服在APP内随时可用。",
                "服务");

        addDocument("账号安全",
                "建议您设置强密码（包含大小写字母+数字+特殊字符），开启双重验证。如发现异常登录，请立即修改密码并联系客服。",
                "账号");

        addDocument("优惠券使用",
                "优惠券可在结算页面使用。每笔订单限用一张。部分商品不参与优惠活动。优惠券有效期为领取后7天。",
                "促销");

        log.info("Seeded {} knowledge documents across {} categories",
                documents.size(), getCategories().size());
    }

    // ========================================
    // Result type
    // ========================================

    public record SearchResult(Document document, double score) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", document.getId(),
                    "title", document.getTitle(),
                    "content", document.getContent(),
                    "category", document.getCategory(),
                    "score", String.format("%.2f", score),
                    "preview", document.getPreview()
            );
        }
    }
}
