package com.ai.learning.security.evaluation;

import java.util.*;

/**
 * 评估报告 — 结构化输出评估结果
 */
public class EvaluationReport {
    private String title;
    private long timestamp;
    private int totalCases;
    private int passed;
    private int failed;
    private double accuracy;
    private Map<String, Double> categoryAccuracy = new LinkedHashMap<>();
    private Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private List<EvaluationResult> details = new ArrayList<>();
    private Map<String, Object> metrics = new LinkedHashMap<>();

    // 汇总维度
    private double toolCallAccuracy;
    private double attackBlockRate;
    private double avgLatencyMs;

    public EvaluationReport() {
        this.timestamp = System.currentTimeMillis();
    }

    public EvaluationReport(String title) {
        this();
        this.title = title;
    }

    // ---- 构建报告 ----

    public void addResult(EvaluationResult result) {
        details.add(result);
        if (result.isPassed()) passed++;
        else failed++;

        categoryCounts.merge(result.getCategory(), 1, Integer::sum);
    }

    public void finalizeReport() {
        totalCases = details.size();
        accuracy = totalCases > 0 ? (double) passed / totalCases : 0;

        // 分类准确率
        for (String cat : categoryCounts.keySet()) {
            long catPass = details.stream()
                    .filter(r -> r.getCategory().equals(cat) && r.isPassed())
                    .count();
            int catTotal = categoryCounts.get(cat);
            categoryAccuracy.put(cat, catTotal > 0 ? (double) catPass / catTotal : 0);
        }

        // 工具调用准确率
        long toolCases = details.stream()
                .filter(r -> r.getToolUsed() != null && !r.getToolUsed().isEmpty())
                .count();
        long toolPass = details.stream()
                .filter(r -> r.getToolUsed() != null && !r.getToolUsed().isEmpty()
                        && r.getExpectedTool() != null && r.getExpectedTool().equals(r.getToolUsed()))
                .count();
        toolCallAccuracy = toolCases > 0 ? (double) toolPass / toolCases : 0;

        // 攻击拦截率
        long attackCases = details.stream().filter(r -> r.isAttackTest()).count();
        long blockedCorrectly = details.stream()
                .filter(r -> r.isAttackTest() && r.isPassed())
                .count();
        attackBlockRate = attackCases > 0 ? (double) blockedCorrectly / attackCases : 0;

        // 延迟平均值
        avgLatencyMs = details.stream()
                .filter(r -> r.getLatencyMs() > 0)
                .mapToLong(EvaluationResult::getLatencyMs)
                .average()
                .orElse(0);

        // 指标
        metrics.put("totalCases", totalCases);
        metrics.put("passed", passed);
        metrics.put("failed", failed);
        metrics.put("accuracy", Math.round(accuracy * 1000) / 10.0);
        metrics.put("toolCallAccuracy", Math.round(toolCallAccuracy * 1000) / 10.0);
        metrics.put("attackBlockRate", Math.round(attackBlockRate * 1000) / 10.0);
        metrics.put("avgLatencyMs", Math.round(avgLatencyMs));
    }

    // ---- 报告输出 ----

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("**测试时间**: ").append(new Date(timestamp)).append("\n\n");

        sb.append("## 总体统计\n\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|----|\n");
        sb.append(String.format("| 总用例 | %d |\n", totalCases));
        sb.append(String.format("| 通过 | %d |\n", passed));
        sb.append(String.format("| 失败 | %d |\n", failed));
        sb.append(String.format("| 总准确率 | %.1f%% |\n", accuracy * 100));
        sb.append(String.format("| 工具调用准确率 | %.1f%% |\n", toolCallAccuracy * 100));
        sb.append(String.format("| 攻击拦截率 | %.1f%% |\n", attackBlockRate * 100));
        sb.append(String.format("| 平均延迟 | %.0fms |\n", avgLatencyMs));

        sb.append("\n## 按类别\n\n");
        sb.append("| 类别 | 用例数 | 准确率 |\n");
        sb.append("|------|:------:|:------:|\n");
        for (Map.Entry<String, Double> entry : categoryAccuracy.entrySet()) {
            sb.append(String.format("| %s | %d | %.1f%% |\n",
                    entry.getKey(), categoryCounts.get(entry.getKey()), entry.getValue() * 100));
        }

        sb.append("\n## 失败的用例\n\n");
        sb.append("| ID | 输入 | 预期 | 实际 | 类别 |\n");
        sb.append("|----|------|------|------|------|\n");
        for (EvaluationResult r : details) {
            if (!r.isPassed()) {
                sb.append(String.format("| %s | %s | %s | %s | %s |\n",
                        r.getTestCaseId(),
                        truncate(r.getInput(), 30),
                        r.isAttackTest() ? "应拦截" : r.getExpectedOutput(),
                        r.getError() != null ? r.getError() : truncate(r.getActualOutput(), 30),
                        r.getCategory()));
            }
        }

        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ---- Getters ----

    public String getTitle() { return title; }
    public int getTotalCases() { return totalCases; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public double getAccuracy() { return accuracy; }
    public Map<String, Double> getCategoryAccuracy() { return categoryAccuracy; }
    public List<EvaluationResult> getDetails() { return details; }
    public Map<String, Object> getMetrics() { return metrics; }
    public double getToolCallAccuracy() { return toolCallAccuracy; }
    public double getAttackBlockRate() { return attackBlockRate; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
}
