package com.ai.learning.dag.service;

import com.ai.learning.dag.model.DagNode;
import com.ai.learning.dag.model.NodeType;
import com.ai.learning.dag.graph.DagGraph;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * DAG 工作流服务 — 从 JSON/Java 代码加载和构建工作流
 */
@Service
public class DagWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(DagWorkflowService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 JSON 文件加载工作流定义
     */
    public DagGraph loadFromJson(String jsonPath) throws IOException {
        // 尝试多个路径
        Path path = findWorkflowFile(jsonPath);
        log.info("📂 加载工作流: {}", path.toAbsolutePath());

        String json = Files.readString(path);
        return parseJson(json);
    }

    /**
     * 从 JSON 字符串解析工作流
     */
    public DagGraph parseJson(String json) throws IOException {
        Map<String, Object> workflowDef = objectMapper.readValue(json, new TypeReference<>() {});
        return buildGraphFromMap(workflowDef);
    }

    /**
     * 将 Map 定义转为 DagGraph
     */
    @SuppressWarnings("unchecked")
    public DagGraph buildGraphFromMap(Map<String, Object> workflowDef) {
        DagGraph graph = new DagGraph();
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowDef.get("nodes");

        if (nodes == null) {
            throw new IllegalArgumentException("工作流定义中缺少 'nodes' 字段");
        }

        for (Map<String, Object> nodeDef : nodes) {
            String id = (String) nodeDef.get("id");
            String typeStr = (String) nodeDef.get("type");
            NodeType type = NodeType.valueOf(typeStr.toUpperCase());

            DagNode node = new DagNode(id, type);

            if (nodeDef.containsKey("dependencies")) {
                node.setDependencies(new ArrayList<>(
                        (List<String>) nodeDef.get("dependencies")));
            }

            if (nodeDef.containsKey("config")) {
                node.setConfig(new HashMap<>(
                        (Map<String, Object>) nodeDef.get("config")));
            }

            graph.addNode(node);
        }

        return graph;
    }

    /**
     * 构建一个示例工作流
     */
    public DagGraph buildSampleWeatherCalcWorkflow() {
        DagGraph graph = new DagGraph();

        graph.addNode(new DagNode("start", NodeType.START));
        graph.addNode(new DagNode("weather", NodeType.TOOL, List.of("start")));
        graph.addNode(new DagNode("calculator", NodeType.TOOL, List.of("start")));
        graph.addNode(new DagNode("llm_summary", NodeType.LLM, List.of("weather", "calculator")));
        graph.addNode(new DagNode("end", NodeType.END, List.of("llm_summary")));

        // 配置
        graph.getNode("weather").getConfig().put("toolName", "weather");
        graph.getNode("weather").getConfig().put("params", "{_input}");

        graph.getNode("calculator").getConfig().put("toolName", "calculator");
        graph.getNode("calculator").getConfig().put("params", "{_input}");

        graph.getNode("llm_summary").getConfig().put("prompt",
                "根据以下信息生成一段简洁的中文总结（不要使用模板变量语法）：\n天气: {weather}\n计算: {calculator}\n输入: {_input}");
        graph.getNode("llm_summary").getConfig().put("model", "deepseek-chat");

        graph.getNode("end").getConfig().put("output", "工作流完成: {_summary}");

        return graph;
    }

    /**
     * 构建问答工作流（带条件分支）
     */
    public DagGraph buildQaWorkflow() {
        DagGraph graph = new DagGraph();

        graph.addNode(new DagNode("start", NodeType.START));
        graph.addNode(new DagNode("analyze", NodeType.LLM, List.of("start")));
        graph.addNode(new DagNode("needs_search", NodeType.CONDITION, List.of("analyze")));
        graph.addNode(new DagNode("search_tool", NodeType.TOOL, List.of("needs_search")));
        graph.addNode(new DagNode("direct_answer", NodeType.LLM, List.of("needs_search")));
        graph.addNode(new DagNode("final_answer", NodeType.LLM, List.of("search_tool", "direct_answer")));
        graph.addNode(new DagNode("end", NodeType.END, List.of("final_answer")));

        // 配置
        graph.getNode("analyze").getConfig().put("prompt",
                "分析用户问题，判断是否需要搜索实时信息：\n输入: {_input}\n如果问题涉及实时数据、天气、新闻等，回答'需要搜索'。否则回答'直接回答'。");
        graph.getNode("needs_search").getConfig().put("condition", "analyze contains 需要搜索");
        graph.getNode("needs_search").getConfig().put("trueBranch", "search_tool");
        graph.getNode("needs_search").getConfig().put("falseBranch", "direct_answer");
        graph.getNode("search_tool").getConfig().put("toolName", "web_search");
        graph.getNode("search_tool").getConfig().put("params", "{_input}");
        graph.getNode("direct_answer").getConfig().put("prompt", "请回答用户问题: {_input}");
        graph.getNode("final_answer").getConfig().put("prompt",
                "综合以下信息给出最终答案：\n搜索工具结果: {search_tool}\n直接回答: {direct_answer}\n原始问题: {_input}");
        graph.getNode("end").getConfig().put("output", "问答完成: {_summary}");

        return graph;
    }

    /**
     * 列出所有可用工作流
     */
    public List<String> listAvailableWorkflows() {
        List<String> workflows = new ArrayList<>();
        workflows.add("sample (天气+计算 — 无分支，并行执行)");
        workflows.add("qa (问答 — 含条件分支)");
        return workflows;
    }

    // ========== 辅助方法 ==========

    private Path findWorkflowFile(String jsonPath) {
        // 检查绝对路径
        Path absPath = Paths.get(jsonPath);
        if (Files.exists(absPath)) return absPath;

        // 检查 workflows/ 目录
        Path workflowDir = Paths.get("workflows");
        if (Files.exists(workflowDir.resolve(jsonPath))) {
            return workflowDir.resolve(jsonPath);
        }
        if (Files.exists(workflowDir.resolve(jsonPath + ".json"))) {
            return workflowDir.resolve(jsonPath + ".json");
        }

        // 检查相对项目目录
        Path projectWorkflows = Paths.get("dag-engine/workflows");
        if (Files.exists(projectWorkflows.resolve(jsonPath))) {
            return projectWorkflows.resolve(jsonPath);
        }
        if (Files.exists(projectWorkflows.resolve(jsonPath + ".json"))) {
            return projectWorkflows.resolve(jsonPath + ".json");
        }

        // 默认返回原路径
        return absPath;
    }
}
