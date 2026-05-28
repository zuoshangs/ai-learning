package com.ai.learning.security.controller;

import com.ai.learning.security.evaluation.AgentEvaluator;
import com.ai.learning.security.evaluation.EvaluationReport;
import com.ai.learning.security.evaluation.TestCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 评估 API 控制器
 */
@RestController
@RequestMapping("/api/evaluate")
public class EvaluationController {

    private final AgentEvaluator evaluator;

    public EvaluationController(AgentEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 运行默认测试套件
     */
    @PostMapping("/default")
    public ResponseEntity<Map<String, Object>> runDefault() {
        var testCases = AgentEvaluator.createDefaultTestSuite();
        EvaluationReport report = evaluator.evaluate(testCases);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("metrics", report.getMetrics());
        resp.put("categoryAccuracy", report.getCategoryAccuracy());
        resp.put("reportMarkdown", report.toMarkdown());
        resp.put("totalCases", report.getTotalCases());
        resp.put("passed", report.getPassed());
        resp.put("failed", report.getFailed());
        resp.put("accuracy", Math.round(report.getAccuracy() * 1000) / 10.0 + "%");
        resp.put("toolCallAccuracy", Math.round(report.getToolCallAccuracy() * 1000) / 10.0 + "%");
        resp.put("attackBlockRate", Math.round(report.getAttackBlockRate() * 1000) / 10.0 + "%");

        return ResponseEntity.ok(resp);
    }

    /**
     * 运行自定义测试用例
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> runCustom(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> testDefs = (List<Map<String, Object>>) body.get("testCases");
        if (testDefs == null || testDefs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 testCases"));
        }

        List<TestCase> testCases = new ArrayList<>();
        for (Map<String, Object> def : testDefs) {
            TestCase tc = new TestCase();
            tc.setId((String) def.getOrDefault("id", UUID.randomUUID().toString().substring(0, 8)));
            tc.setInput((String) def.getOrDefault("input", ""));
            tc.setExpectedTool((String) def.get("expectedTool"));
            tc.setExpectedOutputContains((String) def.get("expectedOutputContains"));
            tc.setAttack(Boolean.TRUE.equals(def.get("isAttack")));
            tc.setExpectedBlocked(Boolean.TRUE.equals(def.get("expectedBlocked")));
            tc.setCategory((String) def.getOrDefault("category", "custom"));
            testCases.add(tc);
        }

        EvaluationReport report = evaluator.evaluate(testCases);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("metrics", report.getMetrics());
        resp.put("reportMarkdown", report.toMarkdown());

        return ResponseEntity.ok(resp);
    }
}
