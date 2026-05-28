package com.ai.learning.knowledge.search;

import com.ai.learning.knowledge.model.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * LLM Reranker（Day 24 继承）
 * <p>
 * 用 DeepSeek 对搜索结果重新评分（1-5 分），
 * 按 rerank 分数加权重排
 */
@Service
public class RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);

    private final ChatClient chatClient;

    public RerankerService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对搜索结果进行 LLM 重排
     *
     * @param query   原始查询
     * @param results 待重排的结果（取 topK*3 候选）
     * @param topK    最终返回数
     * @return 重排后的结果
     */
    public List<SearchResultItem> rerank(String query, List<SearchResultItem> results, int topK) {
        if (results == null || results.isEmpty()) return results;

        // 取足够多的候选供重排
        List<SearchResultItem> candidates = new ArrayList<>(results);
        if (candidates.size() > topK * 3) {
            candidates = candidates.subList(0, topK * 3);
        }

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
                // 解析 scores 数组
                Pattern pattern = Pattern.compile("\\d+");
                var matcher = pattern.matcher(json);
                List<Integer> scores = new ArrayList<>();
                while (matcher.find()) scores.add(Integer.parseInt(matcher.group()));

                for (int i = 0; i < Math.min(scores.size(), candidates.size()); i++) {
                    candidates.get(i).setRerankScore((double) scores.get(i));
                    // 重排分数 = 原始分 * 0.3 + rerank * 0.7
                    double newScore = candidates.get(i).getScore() * 0.3 + (scores.get(i) / 5.0) * 0.7;
                    candidates.get(i).setScore(Math.round(newScore * 1000.0) / 1000.0);
                }
            }
        } catch (Exception e) {
            log.warn("Reranker 失败，保持原有排序: {}", e.getMessage());
        }

        candidates.sort(Comparator.comparingDouble(SearchResultItem::getScore).reversed());
        List<SearchResultItem> top = candidates.stream().limit(topK).toList();
        for (int i = 0; i < top.size(); i++) top.get(i).setRank(i + 1);

        log.info("🔄 Reranker 完成: {} 候选 → {} 重排", candidates.size(), top.size());
        return top;
    }
}
