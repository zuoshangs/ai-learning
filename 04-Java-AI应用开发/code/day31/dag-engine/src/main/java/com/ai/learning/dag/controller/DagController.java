package com.ai.learning.dag.controller;

import com.ai.learning.dag.executor.DagExecutor;
import com.ai.learning.dag.executor.DagResult;
import com.ai.learning.dag.graph.DagGraph;
import com.ai.learning.dag.service.DagWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DAG 工作流 REST API 控制器
 */
@RestController
@RequestMapping("/api/dag")
public class DagController {
    private static final Logger log = LoggerFactory.getLogger(DagController.class);

    private final DagExecutor executor;
    private final DagWorkflowService workflowService;

    public DagController(DagExecutor executor, DagWorkflowService workflowService) {
        this.executor = executor;
        this.workflowService = workflowService;
    }

    /**
     * 查看可用工作流
     */
    @GetMapping("/workflows")
    public Map<String, Object> listWorkflows() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflows", workflowService.listAvailableWorkflows());
        return result;
    }

    /**
     * 执行预定义工作流
     */
    @PostMapping("/run/{workflowName}")
    public ResponseEntity<Map<String, Object>> runWorkflow(
            @PathVariable String workflowName,
            @RequestBody Map<String, String> body) {

        String input = body.getOrDefault("input", "");
        log.info("🚀 启动工作流 '{}'，输入: {}", workflowName, input);

        DagGraph graph;
        try {
            graph = switch (workflowName) {
                case "sample" -> workflowService.buildSampleWeatherCalcWorkflow();
                case "qa" -> workflowService.buildQaWorkflow();
                default -> throw new IllegalArgumentException("未知工作流: " + workflowName);
            };
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "工作流加载失败: " + e.getMessage()));
        }

        DagResult result = executor.execute(graph, input);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflow", workflowName);
        response.put("input", input);
        response.put("success", result.isSuccess());
        response.put("finalOutput", result.getFinalOutput());
        response.put("elapsedMs", result.getElapsedMs());
        response.put("nodeOutputs", result.getNodeOutputs());
        response.put("failedNodes", result.getFailedNodes());

        // 打印执行图
        log.info("📊 DAG 执行图:\n{}", graph);
        log.info("✅ 工作流完成，耗时 {}ms", result.getElapsedMs());

        return ResponseEntity.ok(response);
    }

    /**
     * 自定义 DAG 执行（提交 JSON 定义）
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> runCustomWorkflow(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        Map<String, Object> workflowDef = (Map<String, Object>) body.get("workflow");
        String input = (String) body.getOrDefault("input", "");

        if (workflowDef == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 'workflow' 字段"));
        }

        try {
            DagGraph graph = workflowService.buildGraphFromMap(workflowDef);
            DagResult result = executor.execute(graph, input);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("finalOutput", result.getFinalOutput());
            response.put("elapsedMs", result.getElapsedMs());
            response.put("nodeOutputs", result.getNodeOutputs());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "执行失败: " + e.getMessage()));
        }
    }

    /**
     * 验证 DAG 定义是否合法
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateWorkflow(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        Map<String, Object> workflowDef = (Map<String, Object>) body.get("workflow");

        if (workflowDef == null) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "缺少 workflow"));
        }

        try {
            DagGraph graph = workflowService.buildGraphFromMap(workflowDef);
            graph.validate();

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "nodes", graph.size(),
                    "message", "DAG 验证通过"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "error", e.getMessage()));
        }
    }
}
