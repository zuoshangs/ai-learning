package com.ai.learning.knowledge.model;

import java.util.*;

/**
 * 评估结果 — V1 vs V2 对比
 */
public class EvaluationResult {

    public static class TestCase {
        private String query;
        private String expectedTopic;
        private String v1Mode;
        private int v1TopK;
        private List<SearchResultItem> v1Results;
        private double v1AvgScore;
        private String v2Mode;
        private List<SearchResultItem> v2Results;
        private double v2AvgScore;
        private double improvementPercent;

        public String getQuery() { return query; }
        public TestCase setQuery(String query) { this.query = query; return this; }

        public String getExpectedTopic() { return expectedTopic; }
        public TestCase setExpectedTopic(String expectedTopic) { this.expectedTopic = expectedTopic; return this; }

        public String getV1Mode() { return v1Mode; }
        public TestCase setV1Mode(String v1Mode) { this.v1Mode = v1Mode; return this; }

        public int getV1TopK() { return v1TopK; }
        public TestCase setV1TopK(int v1TopK) { this.v1TopK = v1TopK; return this; }

        public List<SearchResultItem> getV1Results() { return v1Results; }
        public TestCase setV1Results(List<SearchResultItem> v1Results) { this.v1Results = v1Results; return this; }

        public double getV1AvgScore() { return v1AvgScore; }
        public TestCase setV1AvgScore(double v1AvgScore) { this.v1AvgScore = v1AvgScore; return this; }

        public String getV2Mode() { return v2Mode; }
        public TestCase setV2Mode(String v2Mode) { this.v2Mode = v2Mode; return this; }

        public List<SearchResultItem> getV2Results() { return v2Results; }
        public TestCase setV2Results(List<SearchResultItem> v2Results) { this.v2Results = v2Results; return this; }

        public double getV2AvgScore() { return v2AvgScore; }
        public TestCase setV2AvgScore(double v2AvgScore) { this.v2AvgScore = v2AvgScore; return this; }

        public double getImprovementPercent() { return improvementPercent; }
        public TestCase setImprovementPercent(double improvementPercent) { this.improvementPercent = improvementPercent; return this; }
    }

    private String title;
    private long timestamp;
    private int totalTests;
    private double v1OverallAvgScore;
    private double v2OverallAvgScore;
    private double overallImprovementPercent;
    private List<TestCase> testCases = new ArrayList<>();
    private Map<String, Integer> modeUsage = new LinkedHashMap<>();

    public String getTitle() { return title; }
    public EvaluationResult setTitle(String title) { this.title = title; return this; }

    public long getTimestamp() { return timestamp; }
    public EvaluationResult setTimestamp(long timestamp) { this.timestamp = timestamp; return this; }

    public int getTotalTests() { return totalTests; }
    public EvaluationResult setTotalTests(int totalTests) { this.totalTests = totalTests; return this; }

    public double getV1OverallAvgScore() { return v1OverallAvgScore; }
    public EvaluationResult setV1OverallAvgScore(double v1OverallAvgScore) { this.v1OverallAvgScore = v1OverallAvgScore; return this; }

    public double getV2OverallAvgScore() { return v2OverallAvgScore; }
    public EvaluationResult setV2OverallAvgScore(double v2OverallAvgScore) { this.v2OverallAvgScore = v2OverallAvgScore; return this; }

    public double getOverallImprovementPercent() { return overallImprovementPercent; }
    public EvaluationResult setOverallImprovementPercent(double overallImprovementPercent) { this.overallImprovementPercent = overallImprovementPercent; return this; }

    public List<TestCase> getTestCases() { return testCases; }
    public EvaluationResult setTestCases(List<TestCase> testCases) { this.testCases = testCases; return this; }
    public EvaluationResult addTestCase(TestCase tc) { this.testCases.add(tc); return this; }

    public Map<String, Integer> getModeUsage() { return modeUsage; }
    public EvaluationResult setModeUsage(Map<String, Integer> modeUsage) { this.modeUsage = modeUsage; return this; }
}
