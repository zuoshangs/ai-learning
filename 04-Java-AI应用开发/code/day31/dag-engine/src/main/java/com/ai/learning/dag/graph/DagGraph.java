package com.ai.learning.dag.graph;

import com.ai.learning.dag.model.DagNode;
import com.ai.learning.dag.model.NodeType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DAG 图 — 存储节点拓扑并提执行排序（拓扑排序 + 环检测）
 *
 * 核心能力：
 * 1. 添加/查询节点
 * 2. 拓扑排序（Kahn 算法 + DFS 双校验）
 * 3. 环检测（返回环路径便于调试）
 * 4. 分层执行计划（每层节点可并行执行）
 */
public class DagGraph {
    private final Map<String, DagNode> nodes = new LinkedHashMap<>();

    // ---- 构建 ----

    public void addNode(DagNode node) {
        nodes.put(node.getId(), node);
    }

    public DagNode getNode(String id) {
        return nodes.get(id);
    }

    public List<DagNode> getAllNodes() {
        return List.copyOf(nodes.values());
    }

    public int size() {
        return nodes.size();
    }

    // ---- 拓扑排序（Kahn 算法） ----

    /**
     * 返回拓扑排序结果。
     * 如果存在环，抛出 IllegalArgumentException 并附带环路径信息。
     */
    public List<String> topologicalSort() {
        // 入度表
        Map<String, Integer> inDegree = new HashMap<>();
        for (DagNode node : nodes.values()) {
            inDegree.put(node.getId(), 0);
        }
        for (DagNode node : nodes.values()) {
            for (String dep : node.getDependencies()) {
                inDegree.merge(node.getId(), 1, Integer::sum);
            }
        }

        // 队列（入度为 0 的节点）
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(id);
            // 找所有依赖本节点的下游节点
            for (DagNode node : nodes.values()) {
                if (node.getDependencies().contains(id)) {
                    int newDegree = inDegree.merge(node.getId(), -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(node.getId());
                    }
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            // 找到环中的节点
            Set<String> sortedSet = new HashSet<>(sorted);
            List<String> cycleNodes = nodes.values().stream()
                    .map(DagNode::getId)
                    .filter(n -> !sortedSet.contains(n))
                    .collect(Collectors.toList());
            throw new IllegalArgumentException(
                    "DAG 中存在环！参与环的节点: " + cycleNodes + "。请检查依赖关系。");
        }

        return sorted;
    }

    // ---- DFS 环检测（辅助） ----

    /**
     * 使用 DFS 检测环，返回环路径（空列表表示无环）。
     */
    public List<String> detectCycle() {
        Set<String> white = new HashSet<>(nodes.keySet()); // 未访问
        Set<String> gray = new HashSet<>(); // 正在访问
        Set<String> black = new HashSet<>(); // 已完成

        for (String id : nodes.keySet()) {
            if (white.contains(id)) {
                List<String> cycle = dfsVisit(id, white, gray, black, new ArrayList<>());
                if (!cycle.isEmpty()) return cycle;
            }
        }
        return Collections.emptyList();
    }

    private List<String> dfsVisit(String id, Set<String> white, Set<String> gray,
                                   Set<String> black, List<String> path) {
        white.remove(id);
        gray.add(id);
        path.add(id);

        DagNode node = nodes.get(id);
        if (node != null) {
            for (String dep : node.getDependencies()) {
                if (gray.contains(dep)) {
                    // 找到环——从 dep 到当前路径末尾
                    int startIdx = path.indexOf(dep);
                    return path.subList(startIdx, path.size());
                }
                if (white.contains(dep)) {
                    List<String> result = dfsVisit(dep, white, gray, black, path);
                    if (!result.isEmpty()) return result;
                }
            }
        }

        gray.remove(id);
        black.add(id);
        path.removeLast();
        return Collections.emptyList();
    }

    // ---- 分层执行计划 ----

    /**
     * 将拓扑排序结果按依赖深度分层。
     * 同一层的节点无相互依赖，可以并行执行。
     *
     * 返回 List<List<String>>：外层是执行阶段，内层是该阶段的节点 ID
     */
    public List<List<String>> getLeveledExecutionPlan() {
        List<String> sorted = topologicalSort();

        // 计算每个节点的深度
        Map<String, Integer> depth = new HashMap<>();
        for (String id : sorted) {
            DagNode node = nodes.get(id);
            int maxDepDepth = -1;
            for (String dep : node.getDependencies()) {
                maxDepDepth = Math.max(maxDepDepth, depth.getOrDefault(dep, -1));
            }
            depth.put(id, maxDepDepth + 1);
        }

        // 按深度分组
        Map<Integer, List<String>> levelMap = new TreeMap<>();
        for (String id : sorted) {
            levelMap.computeIfAbsent(depth.get(id), k -> new ArrayList<>()).add(id);
        }

        return new ArrayList<>(levelMap.values());
    }

    // ---- 验证 ----

    /**
     * 全面验证 DAG：
     * - 检查所有依赖的节点是否存在
     * - 检查环
     * - 检查必须的节点类型（必须有一个 START 和一个 END）
     */
    public void validate() {
        // 1. 检查依赖节点存在
        for (DagNode node : nodes.values()) {
            for (String dep : node.getDependencies()) {
                if (!nodes.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "节点 '" + node.getId() + "' 依赖的节点 '" + dep + "' 不存在");
                }
            }
        }

        // 2. 环检测
        List<String> cycle = detectCycle();
        if (!cycle.isEmpty()) {
            throw new IllegalArgumentException(
                    "DAG 中存在环！环路径: " + String.join(" → ", cycle));
        }

        // 3. 检查拓扑排序（双重确认）
        topologicalSort();

        // 4. 检查 START 和 END 节点
        long startCount = nodes.values().stream()
                .filter(n -> n.getType() == NodeType.START).count();
        long endCount = nodes.values().stream()
                .filter(n -> n.getType() == NodeType.END).count();
        if (startCount == 0) {
            throw new IllegalArgumentException("DAG 必须包含至少一个 START 节点");
        }
        if (endCount == 0) {
            throw new IllegalArgumentException("DAG 必须包含至少一个 END 节点");
        }

        // 5. 检查 START 节点不能有依赖
        for (DagNode node : nodes.values()) {
            if (node.getType() == NodeType.START && !node.getDependencies().isEmpty()) {
                throw new IllegalArgumentException(
                        "START 节点 '" + node.getId() + "' 不能有前置依赖");
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DagGraph:\n");
        for (DagNode node : nodes.values()) {
            sb.append("  ").append(node.getId())
              .append(" [").append(node.getType()).append("]");
            if (!node.getDependencies().isEmpty()) {
                sb.append("  ← ").append(String.join(", ", node.getDependencies()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
