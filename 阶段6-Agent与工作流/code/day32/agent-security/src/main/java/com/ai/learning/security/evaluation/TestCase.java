package com.ai.learning.security.evaluation;

/**
 * 测试用例 — 评估 Agent 能力的标准输入
 */
public class TestCase {
    private String id;
    private String input;            // 用户输入
    private String expectedTool;     // 预期调用的工具名
    private String expectedOutputContains; // 预期输出包含的关键词
    private boolean isAttack;        // 是否是攻击测试
    private boolean expectedBlocked; // 预期是否被拦截
    private String category;         // 测试类别: normal/attack/prompt_leak/role_hijack

    public TestCase() {}

    public TestCase(String id, String input, String expectedTool,
                    String expectedOutputContains, boolean isAttack,
                    boolean expectedBlocked, String category) {
        this.id = id;
        this.input = input;
        this.expectedTool = expectedTool;
        this.expectedOutputContains = expectedOutputContains;
        this.isAttack = isAttack;
        this.expectedBlocked = expectedBlocked;
        this.category = category;
    }

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getExpectedTool() { return expectedTool; }
    public void setExpectedTool(String expectedTool) { this.expectedTool = expectedTool; }
    public String getExpectedOutputContains() { return expectedOutputContains; }
    public void setExpectedOutputContains(String expectedOutputContains) { this.expectedOutputContains = expectedOutputContains; }
    public boolean isAttack() { return isAttack; }
    public void setAttack(boolean attack) { isAttack = attack; }
    public boolean isExpectedBlocked() { return expectedBlocked; }
    public void setExpectedBlocked(boolean expectedBlocked) { this.expectedBlocked = expectedBlocked; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String toString() {
        return "TestCase{" + "id='" + id + '\'' + ", input='" + input + '\'' + '}';
    }
}
