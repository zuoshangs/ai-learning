package com.ai.learning.security.evaluation;

import java.util.*;

/**
 * 单条评估结果
 */
public class EvaluationResult {
    private String testCaseId;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private String expectedTool;
    private String toolUsed;
    private boolean attackTest;
    private boolean expectedBlocked;
    private boolean actualBlocked;
    private boolean passed;
    private String category;
    private String error;
    private long latencyMs;

    public EvaluationResult() {}

    // ---- 便捷工厂 ----

    public static EvaluationResult pass(TestCase tc) {
        EvaluationResult r = new EvaluationResult();
        r.testCaseId = tc.getId();
        r.input = tc.getInput();
        r.expectedOutput = tc.getExpectedOutputContains();
        r.expectedTool = tc.getExpectedTool();
        r.attackTest = tc.isAttack();
        r.expectedBlocked = tc.isExpectedBlocked();
        r.category = tc.getCategory();
        r.passed = true;
        return r;
    }

    public static EvaluationResult fail(TestCase tc, String error) {
        EvaluationResult r = pass(tc);
        r.passed = false;
        r.error = error;
        return r;
    }

    // ---- Getters / Setters ----

    public String getTestCaseId() { return testCaseId; }
    public void setTestCaseId(String testCaseId) { this.testCaseId = testCaseId; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
    public String getExpectedTool() { return expectedTool; }
    public void setExpectedTool(String expectedTool) { this.expectedTool = expectedTool; }
    public String getToolUsed() { return toolUsed; }
    public void setToolUsed(String toolUsed) { this.toolUsed = toolUsed; }
    public boolean isAttackTest() { return attackTest; }
    public void setAttackTest(boolean attackTest) { this.attackTest = attackTest; }
    public boolean isExpectedBlocked() { return expectedBlocked; }
    public void setExpectedBlocked(boolean expectedBlocked) { this.expectedBlocked = expectedBlocked; }
    public boolean isActualBlocked() { return actualBlocked; }
    public void setActualBlocked(boolean actualBlocked) { this.actualBlocked = actualBlocked; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
}
