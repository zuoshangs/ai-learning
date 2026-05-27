package com.ai.learning.dto;

import java.util.List;

/**
 * 代码审查结果 — 结构化输出
 */
public class CodeReviewResult {

    private int totalScore;
    private String verdict;        // PASS / MINOR / MAJOR / CRITICAL
    private List<String> strengths;
    private List<Issue> issues;
    private String summary;

    public static class Issue {
        private String severity;   // critical / major / minor / suggestion
        private int line;
        private String description;
        private String suggestion;

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    // Getters and Setters
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    public List<Issue> getIssues() { return issues; }
    public void setIssues(List<Issue> issues) { this.issues = issues; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
