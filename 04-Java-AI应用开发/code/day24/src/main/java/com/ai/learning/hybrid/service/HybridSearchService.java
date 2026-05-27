package com.ai.learning.hybrid.service;

import com.ai.learning.hybrid.model.HybridSearchResponse;
import com.ai.learning.hybrid.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索 + RRF 融合 + LLM Reranker
 *
 * 管线：
 *   用户查询
 *     ↓
 *   语义检索 (PgVector) ─┐
 *   关键词检索 (FTS)  ───┤
 *                         ↓
 *                    RRF 融合排序
 *                         ↓
 *                    LLM Reranker (可选)
 *                         ↓
 *                    RAG 增强 (可选)
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    /** RRF 常数（防止除零，通常为 60） */
    private static final int RRF_K = 60;

    private final VectorSearchService vectorSearch;
    private final KeywordSearchService keywordSearch;
    private final ChatClient chatClient;

    public HybridSearchService(VectorSearchService vectorSearch,
                               KeywordSearchService keywordSearch,
                               ChatClient chatClient) {
        this.vectorSearch = vectorSearch;
        this.keywordSearch = keywordSearch;
        this.chatClient = chatClient;
    }

    /**
     * 混合检索主入口
     *
     * @param query      查询
     * @param mode       模式: semantic / keyword / hybrid / rerank / compare / rag
     * @param topK       结果数
     * @return 检索响应
     */
    public HybridSearchResponse search(String query, String mode, int topK) {
        long t0 = System.currentTimeMillis();

        List<SearchResult> results = switch (mode) {
            case "semantic" -> vectorSearch.search(query, topK);
            case "keyword" -> keywordSearch.search(query, topK);
            case "hybrid" -> rrfFusion(query, topK);
            case "rerank" -> rerankResults(query, topK);
            case "compare" -> compareMethods(query, topK);
            case "rag" -> ragSearch(query, topK);
            default -> rrfFusion(query, topK);
        };

        long t1 = System.currentTimeMillis();

        return new HybridSearchResponse()
            .setQuery(query)
            .setMode(mode)
            .setTotalResults(results.size())
            .setDurationMs(t1 - t0)
            .setResults(results);
    }

    // ====== RRF 融合 ======

    /**
     * Reciprocal Rank Fusion (RRF)
     *
     * 将语义检索和关键词检索的排名融合：
     *   RRF_score(d) = 1/(k + rank_semantic(d)) + 1/(k + rank_keyword(d))
     *
     * 优点：不需要归一化分数，直接用排名计算
     */
    private List<SearchResult> rrfFusion(String query, int topK) {
        // 1. 分别检索（取更多结果保证覆盖率）
        List<SearchResult> semanticResults = vectorSearch.search(query, topK * 2);
        List<SearchResult> keywordResults = keywordSearch.search(query, topK * 2);

        // 2. 构建 内容→排名 映射
        Map<String, Integer> semanticRank = new HashMap<>();
        for (int i = 0; i < semanticResults.size(); i++) {
            semanticRank.put(semanticResults.get(i).getContent(), i + 1);
        }

        Map<String, Integer> keywordRank = new HashMap<>();
        for (int i = 0; i < keywordResults.size(); i++) {
            keywordRank.put(keywordResults.get(i).getContent(), i + 1);
        }

        // 3. RRF 计算
        Set<String> allContents = new LinkedHashSet<>();
        semanticResults.forEach(r -> allContents.add(r.getContent()));
        keywordResults.forEach(r -> allContents.add(r.getContent()));

        List<SearchResult> fused = new ArrayList<>();
        for (String content : allContents) {
            int rankS = semanticRank.getOrDefault(content, Integer.MAX_VALUE);
            int rankK = keywordRank.getOrDefault(content, Integer.MAX_VALUE);

            double rrfScore = 0;
            if (rankS != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankS);
            if (rankK != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankK);

            // 取第一个来源的数据
            SearchResult base = semanticResults.stream()
                .filter(r -> r.getContent().equals(content))
                .findFirst()
                .orElseGet(() -> keywordResults.stream()
                    .filter(r -> r.getContent().equals(content))
                    .findFirst().orElse(null));

            if (base != null) {
                fused.add(new SearchResult()
                    .setScore(Math.round(rrfScore * 10000.0) / 10000.0)
                    .setContent(content)
                    .setContentLength(content.length())
                    .setSource(base.getSource())
                    .setMethod("hybrid")
                    .setSemanticScore(rankS != Integer.MAX_VALUE
                        ? semanticResults.stream().filter(r -> r.getContent().equals(content))
                            .findFirst().map(SearchResult::getScore).orElse(0.0) : 0)
                    .setKeywordScore(rankK != Integer.MAX_VALUE
                        ? keywordResults.stream().filter(r -> r.getContent().equals(content))
                            .findFirst().map(SearchResult::getScore).orElse(0.0) : 0));
            }
        }

        // 4. 按 RRF 分数降序排列
        fused.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());

        // 5. 截取 topK + 编号
        List<SearchResult> top = fused.stream().limit(topK).toList();
        for (int i = 0; i < top.size(); i++) {
            top.get(i).setRank(i + 1);
        }

        // 打印来源统计
        long semanticOnly = top.stream().filter(r -> r.getKeywordScore() == 0).count();
        long keywordOnly = top.stream().filter(r -> r.getSemanticScore() == 0).count();
        long bothMatch = top.stream().filter(r -> r.getSemanticScore() > 0 && r.getKeywordScore() > 0).count();
        log.info("🔀 RRF 融合: 语义独有={}, 关键词独有={}, 重叠={}", semanticOnly, keywordOnly, bothMatch);

        return top;
    }

    // ====== LLM Reranker ======

    /**
     * LLM Reranker — 用 DeepSeek 对结果重新打分
     *
     * 每个结果都会被评分 1-5：
     *   5 = 完全相关
     *   3 = 部分相关
     *   1 = 不相关
     */
    private List<SearchResult> rerankResults(String query, int topK) {
        // 先用 RRF 获取更多候选
        List<SearchResult> candidates = new ArrayList<>(rrfFusion(query, topK * 3));

        if (candidates.isEmpty()) return candidates;

        // 构建评分 prompt
        StringBuilder sb = new StringBuilder();
        sb.append("你是搜索结果相关性评分系统。\n");
        sb.append("用户查询: ").append(query).append("\n\n");
        sb.append("请对以下").append(candidates.size()).append("个结果分别评分(1-5分)：\n");
        sb.append("5=完全匹配查询意图, 4=高度相关, 3=部分相关, 2=弱相关, 1=不相关\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            String preview = candidates.get(i).getContent();
            if (preview.length() > 200) preview = preview.substring(0, 200) + "...";
            sb.append("---结果").append(i + 1).append("---\n");
            sb.append(preview).append("\n\n");
        }

        sb.append("只回复JSON格式：{\"scores\":[5,3,4,...]}\n");

        try {
            String resp = chatClient.prompt().user(sb.toString()).call().content();
            if (resp != null && resp.contains("\"scores\"")) {
                // 提取 JSON
                String json = resp;
                if (resp.contains("```json")) {
                    json = resp.split("```json")[1].split("```")[0];
                } else if (resp.contains("```")) {
                    json = resp.split("```")[1].split("```")[0];
                }
                // 简单解析 scores 数组
                var matcher = java.util.regex.Pattern.compile("\\d+").matcher(json);
                List<Integer> scores = new ArrayList<>();
                while (matcher.find()) scores.add(Integer.parseInt(matcher.group()));

                for (int i = 0; i < Math.min(scores.size(), candidates.size()); i++) {
                    candidates.get(i).setRerankScore(scores.get(i));
                    // 重排分数 = RRF + rerank 加权
                    double newScore = candidates.get(i).getScore() * 0.3 + (scores.get(i) / 5.0) * 0.7;
                    candidates.get(i).setScore(Math.round(newScore * 1000.0) / 1000.0);
                }
            }
        } catch (Exception e) {
            log.warn("Reranker 失败，保持原有排序: {}", e.getMessage());
        }

        candidates.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        List<SearchResult> top = candidates.stream().limit(topK).toList();
        for (int i = 0; i < top.size(); i++) top.get(i).setRank(i + 1);

        log.info("🔄 Reranker 完成: {} 条重排", top.size());
        return top;
    }

    // ====== 策略对比 ======

    private List<SearchResult> compareMethods(String query, int topK) {
        List<SearchResult> combined = new ArrayList<>();

        // 语义检索
        var semantic = vectorSearch.search(query, topK);
        semantic.forEach(r -> r.setMethod("semantic"));
        combined.addAll(semantic);

        // 关键词检索
        var keyword = keywordSearch.search(query, topK);
        keyword.forEach(r -> r.setMethod("keyword"));
        combined.addAll(keyword);

        // RRF 融合
        var hybrid = rrfFusion(query, topK);
        hybrid.forEach(r -> r.setMethod("hybrid"));
        combined.addAll(hybrid);

        // 按方法分组排序
        combined.sort(Comparator.comparing(SearchResult::getMethod)
            .thenComparing(Comparator.comparingDouble(SearchResult::getScore).reversed()));

        for (int i = 0; i < combined.size(); i++) combined.get(i).setRank(i + 1);
        return combined;
    }

    // ====== RAG ======

    private List<SearchResult> ragSearch(String query, int topK) {
        List<SearchResult> results = rrfFusion(query, topK * 2);
        List<SearchResult> top = results.stream().limit(topK).toList();

        return new ArrayList<>(top);
    }
}
