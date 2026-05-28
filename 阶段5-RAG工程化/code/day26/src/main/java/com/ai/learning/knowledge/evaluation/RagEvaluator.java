package com.ai.learning.knowledge.evaluation;

import com.ai.learning.knowledge.model.EvaluationResult;
import com.ai.learning.knowledge.model.SearchRequest;
import com.ai.learning.knowledge.model.SearchResponse;
import com.ai.learning.knowledge.search.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 评估服务 — V1 vs V2 对比
 * <p>
 * 用一组预定义的测试查询，分别用 V1（基础语义检索）
 * 和 V2（全管线：重写+混合检索+Reranker）测试，
 * 对比得分和效果。
 */
@Service
public class RagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluator.class);

    private final KnowledgeBaseService kbService;

    public RagEvaluator(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    /**
     * 运行评估
     *
     * @param testQueries 测试查询列表
     * @param topK        每个查询返回结果数
     * @return 评估报告
     */
    public EvaluationResult evaluate(List<String> testQueries, int topK) {
        long t0 = System.currentTimeMillis();
        log.info("🧪 开始 RAG 评估: {} 条测试查询, topK={}", testQueries.size(), topK);

        EvaluationResult result = new EvaluationResult()
            .setTitle("RAG V1 vs V2 评估报告")
            .setTimestamp(System.currentTimeMillis())
            .setTotalTests(testQueries.size());

        double v1TotalScore = 0;
        double v2TotalScore = 0;
        Map<String, Integer> modeUsage = new LinkedHashMap<>();

        for (String query : testQueries) {
            try {
                // V1 — 基础语义检索（无重写、无混合、无重排）
                SearchRequest v1Req = new SearchRequest()
                    .setQuery(query)
                    .setTopK(topK)
                    .setUseRewrite(false)
                    .setUseHyde(false)
                    .setUseParentDoc(false)
                    .setUseHybridSearch(false)
                    .setUseReranker(false);
                SearchResponse v1Resp = kbService.searchV2(v1Req);

                // V2 — 全管线（最佳配置）
                SearchRequest v2Req = new SearchRequest()
                    .setQuery(query)
                    .setTopK(topK)
                    .setUseRewrite(true)
                    .setUseHyde(false)
                    .setUseParentDoc(true)
                    .setUseHybridSearch(true)
                    .setUseReranker(true);
                SearchResponse v2Resp = kbService.searchV2(v2Req);

                // 计算平均分
                double v1Avg = v1Resp.getResults().stream()
                    .mapToDouble(r -> r.getScore()).average().orElse(0);
                double v2Avg = v2Resp.getResults().stream()
                    .mapToDouble(r -> r.getScore()).average().orElse(0);

                double improvement = v1Avg > 0
                    ? Math.round(((v2Avg - v1Avg) / v1Avg) * 10000.0) / 100.0
                    : 0;

                v1TotalScore += v1Avg;
                v2TotalScore += v2Avg;

                // 跟踪各管线组件使用
                modeUsage.merge("V1: " + v1Resp.getPipeline(), 1, Integer::sum);
                modeUsage.merge("V2: " + v2Resp.getPipeline(), 1, Integer::sum);

                // 提取预期主题（从查询中简单推断）
                String topic = extractTopic(query);

                EvaluationResult.TestCase tc = new EvaluationResult.TestCase();
                tc.setQuery(query);
                tc.setExpectedTopic(topic);
                tc.setV1Mode(v1Resp.getPipeline());
                tc.setV1TopK(v1Resp.getResults().size());
                tc.setV1Results(v1Resp.getResults());
                tc.setV1AvgScore(Math.round(v1Avg * 1000.0) / 1000.0);
                tc.setV2Mode(v2Resp.getPipeline());
                tc.setV2Results(v2Resp.getResults());
                tc.setV2AvgScore(Math.round(v2Avg * 1000.0) / 1000.0);
                tc.setImprovementPercent(improvement);

                result.addTestCase(tc);

                log.info("  {}: V1={} → V2={} ({}%)",
                    query, tc.getV1AvgScore(), tc.getV2AvgScore(),
                    tc.getImprovementPercent());

            } catch (Exception e) {
                log.warn("  {} 评估失败: {}", query, e.getMessage());
            }
        }

        // 总体统计
        result.setV1OverallAvgScore(Math.round(v1TotalScore / testQueries.size() * 1000.0) / 1000.0);
        result.setV2OverallAvgScore(Math.round(v2TotalScore / testQueries.size() * 1000.0) / 1000.0);
        result.setOverallImprovementPercent(
            result.getV1OverallAvgScore() > 0
                ? Math.round(((result.getV2OverallAvgScore() - result.getV1OverallAvgScore())
                    / result.getV1OverallAvgScore()) * 10000.0) / 100.0
                : 0);
        result.setModeUsage(modeUsage);

        long duration = System.currentTimeMillis() - t0;
        log.info("🧪 评估完成: V1 avg={}, V2 avg={}, 提升={}% [{}ms]",
            result.getV1OverallAvgScore(), result.getV2OverallAvgScore(),
            result.getOverallImprovementPercent(), duration);

        return result;
    }

    /**
     * 快速评估 — 用预定义测试集
     */
    public EvaluationResult quickEval(int topK) {
        List<String> testQueries = Arrays.asList(
            "PgVector 向量数据库的特点",
            "什么是 RAG",
            "查询重写怎么用",
            "HyDE 的原理",
            "RRF 融合排序"
        );
        return evaluate(testQueries, topK);
    }

    private String extractTopic(String query) {
        // 简单提取：取最后一个技术名词
        String[] keywords = query.split("[\\s，。、]");
        if (keywords.length > 0) {
            return keywords[keywords.length - 1];
        }
        return query;
    }
}
