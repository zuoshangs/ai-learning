package com.ai.learning.knowledge.search;

import com.ai.learning.knowledge.model.SearchRequest;
import com.ai.learning.knowledge.model.SearchResponse;
import com.ai.learning.knowledge.model.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库集成服务 — 知识库 V2 的核心
 * <p>
 * 集成管线：
 * <pre>
 *   用户查询
 *      ↓
 *   (可选) 查询重写 ─→ 更精确的关键词
 *      ↓
 *   (可选) HyDE ───→ 假设回答检索
 *      ↓
 *   ┌─ 混合检索 (RRF 融合) ─┐
 *   │  语义检索   关键词检索  │
 *   └──────────────────────┘
 *      ↓
 *   (可选) 父文档检索 ─→ 返回完整上下文
 *      ↓
 *   (可选) LLM Reranker ─→ 重排
 *      ↓
 *   LLM 回答生成 ─→ 最终答案
 * </pre>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final ChatClient chatClient;
    private final QueryRewriteService rewriteService;
    private final VectorSearchService vectorSearch;
    private final KeywordSearchService keywordSearch;
    private final RerankerService rerankerService;

    /** RRF 常数 */
    private static final int RRF_K = 60;

    public KnowledgeBaseService(ChatClient chatClient,
                                QueryRewriteService rewriteService,
                                VectorSearchService vectorSearch,
                                KeywordSearchService keywordSearch,
                                RerankerService rerankerService) {
        this.chatClient = chatClient;
        this.rewriteService = rewriteService;
        this.vectorSearch = vectorSearch;
        this.keywordSearch = keywordSearch;
        this.rerankerService = rerankerService;
    }

    // ============================================
    //  V2 集成搜索（全管线）
    // ============================================

    /**
     * V2 集成搜索 — 全管线
     */
    public SearchResponse searchV2(SearchRequest req) {
        long t0 = System.currentTimeMillis();
        String query = req.getQuery();
        int topK = req.getTopK();

        StringBuilder pipelineDesc = new StringBuilder("V2 Full Pipeline");
        SearchResponse response = new SearchResponse()
            .setQuery(query);

        // ---- 1. 查询重写 ----
        String searchQuery = query;
        if (req.isUseRewrite()) {
            searchQuery = rewriteService.rewrite(query);
            response.setRewrittenQuery(searchQuery);
            pipelineDesc.append(" [Rewrite]");
        }

        // ---- 2. HyDE ----
        String hydeQuery = searchQuery;
        if (req.isUseHyde()) {
            String hypothesis = generateHypothesis(searchQuery);
            response.setHydeHypothesis(hypothesis);
            hydeQuery = hypothesis + " " + searchQuery;
            pipelineDesc.append(" [HyDE]");
        }

        // ---- 3. 检索 ----
        List<SearchResultItem> results;
        if (req.isUseHybridSearch()) {
            results = hybridSearch(hydeQuery, topK);
            pipelineDesc.append(" [Hybrid]");
        } else {
            results = vectorSearch.search(hydeQuery, topK);
            pipelineDesc.append(" [Semantic]");
        }

        // ---- 4. 父文档检索 ----
        if (req.isUseParentDoc() && !results.isEmpty()) {
            results = expandParentDocs(results);
            pipelineDesc.append(" [ParentDoc]");
        }

        // ---- 5. Reranker ----
        if (req.isUseReranker() && !results.isEmpty()) {
            results = rerankerService.rerank(query, results, topK);
            pipelineDesc.append(" [Rerank]");
        }

        response.setResults(results);

        // ---- 6. 回答生成 ----
        String answer = generateAnswer(query, results);
        response.setAnswer(answer);

        long t1 = System.currentTimeMillis();
        response.setPipeline(pipelineDesc.toString());
        response.setDurationMs(t1 - t0);
        response.addMetadata("totalResults", results.size());

        log.info("🏭 V2 管线完成: {} [{}ms]", pipelineDesc, t1 - t0);
        return response;
    }

    // ============================================
    //  V1 基础检索（对比基准）
    // ============================================

    /**
     * V1 基础检索 — 仅语义检索 + 直接回答
     * 作为对比基准
     */
    public SearchResponse searchV1(String query, int topK) {
        long t0 = System.currentTimeMillis();
        List<SearchResultItem> results = vectorSearch.search(query, topK);

        SearchResponse response = new SearchResponse()
            .setQuery(query)
            .setPipeline("V1 Basic [Semantic-only]")
            .setResults(results)
            .setAnswer(generateAnswer(query, results))
            .setDurationMs(System.currentTimeMillis() - t0);

        log.info("🏭 V1 基础检索完成 [{}ms]", response.getDurationMs());
        return response;
    }

    // ============================================
    //  混合检索 (RRF)
    // ============================================

    private List<SearchResultItem> hybridSearch(String query, int topK) {
        // 1. 分别检索
        List<SearchResultItem> semanticResults = vectorSearch.search(query, topK * 2);
        List<SearchResultItem> keywordResults = keywordSearch.search(query, topK * 2);

        // 2. 构建 内容→排名 映射
        Map<String, Integer> semanticRank = new HashMap<>();
        for (int i = 0; i < semanticResults.size(); i++)
            semanticRank.put(semanticResults.get(i).getContent(), i + 1);

        Map<String, Integer> keywordRank = new HashMap<>();
        for (int i = 0; i < keywordResults.size(); i++)
            keywordRank.put(keywordResults.get(i).getContent(), i + 1);

        // 3. RRF 融合
        Set<String> allContents = new LinkedHashSet<>();
        semanticResults.forEach(r -> allContents.add(r.getContent()));
        keywordResults.forEach(r -> allContents.add(r.getContent()));

        List<SearchResultItem> fused = new ArrayList<>();
        for (String content : allContents) {
            int rankS = semanticRank.getOrDefault(content, Integer.MAX_VALUE);
            int rankK = keywordRank.getOrDefault(content, Integer.MAX_VALUE);

            double rrfScore = 0;
            if (rankS != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankS);
            if (rankK != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankK);

            SearchResultItem base = semanticResults.stream()
                .filter(r -> r.getContent().equals(content))
                .findFirst()
                .orElseGet(() -> keywordResults.stream()
                    .filter(r -> r.getContent().equals(content))
                    .findFirst().orElse(null));

            if (base != null) {
                fused.add(new SearchResultItem()
                    .setScore(Math.round(rrfScore * 10000.0) / 10000.0)
                    .setContent(content)
                    .setContentLength(content.length())
                    .setSource(base.getSource())
                    .setMethod("hybrid"));
            }
        }

        fused.sort(Comparator.comparingDouble(SearchResultItem::getScore).reversed());
        return fused.stream().limit(topK).toList();
    }

    // ============================================
    //  父文档检索
    // ============================================

    private List<SearchResultItem> expandParentDocs(List<SearchResultItem> chunkResults) {
        // 将小块替换为完整父文档
        // 按 parentDocId 分组
        Map<String, List<SearchResultItem>> grouped = chunkResults.stream()
            .collect(Collectors.groupingBy(
                r -> r.getSource() + "::" + r.getContent().hashCode(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        // 实际上父文档检索需要从向量库获取
        // 这里用简化方式：如果检索到的块来自同一文档，就拼接在一起
        Map<String, StringBuilder> parentMap = new LinkedHashMap<>();
        for (SearchResultItem r : chunkResults) {
            String key = r.getSource();
            parentMap.computeIfAbsent(key, k -> new StringBuilder());
            if (parentMap.get(key).length() > 0) {
                parentMap.get(key).append("\n\n...\n\n");
            }
            parentMap.get(key).append(r.getContent());
        }

        List<SearchResultItem> parentResults = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, StringBuilder> entry : parentMap.entrySet()) {
            String fullContent = entry.getValue().toString();
            parentResults.add(new SearchResultItem()
                .setRank(rank++)
                .setScore(0.9)
                .setContent(fullContent)
                .setContentLength(fullContent.length())
                .setSource(entry.getKey())
                .setMethod("parent-doc")
                .setParentContent(fullContent));
        }

        log.info("📂 父文档检索: {} 个块 → {} 个父文档", chunkResults.size(), parentResults.size());
        return parentResults;
    }

    // ============================================
    //  HyDE 假设回答生成
    // ============================================

    private String generateHypothesis(String query) {
        try {
            String hydePrompt = """
                你是一个领域专家。根据用户的问题，假设你有一份完美的参考文档，
                请写出这篇文档中可能包含的段落。

                要求：
                1. 假设回答的语气要像真实的参考文档（用第三人称、客观陈述）
                2. 包含具体的技术名词和术语
                3. 长度在 100-200 字之间
                4. 只输出假设文档内容，不要额外说明

                用户问题：%s
                """;

            String hypothesis = chatClient.prompt()
                .user(String.format(hydePrompt, query))
                .call()
                .content();

            if (hypothesis == null || hypothesis.isBlank()) return query;
            log.info("🤔 HyDE: {} 字", hypothesis.trim().length());
            return hypothesis.trim();

        } catch (Exception e) {
            log.warn("HyDE 失败，回退原始查询: {}", e.getMessage());
            return query;
        }
    }

    // ============================================
    //  回答生成
    // ============================================

    private String generateAnswer(String query, List<SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return "未找到相关文档，无法回答。";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            SearchResultItem r = results.get(i);
            context.append("【文档").append(i + 1).append("】\n");
            context.append(r.getContent()).append("\n\n");
        }

        String answerPrompt = """
            你是一个知识库问答助手。请基于以下参考文档回答用户的问题。

            要求：
            1. 只使用参考文档中的信息，不要编造
            2. 如果文档信息不足，请明确说明
            3. 回答要简洁、准确
            4. 如果涉及代码或配置，给出具体的示例

            参考文档：
            %s

            用户问题：%s

            请给出回答：
            """;

        try {
            return chatClient.prompt()
                .user(String.format(answerPrompt, context.toString(), query))
                .call()
                .content();
        } catch (Exception e) {
            log.warn("回答生成失败: {}", e.getMessage());
            return "基于" + results.size() + "篇相关文档，正在处理中...";
        }
    }
}
