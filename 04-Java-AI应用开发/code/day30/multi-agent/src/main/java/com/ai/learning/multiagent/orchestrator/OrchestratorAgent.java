package com.ai.learning.multiagent.orchestrator;

import com.ai.learning.multiagent.core.*;
import com.ai.learning.multiagent.worker.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 编排器 Agent —— 多 Agent 协作的核心。
 * 
 * 路由策略：
 * 1. 分析用户输入 → 选出合适的 Worker（通过 canHandle）
 * 2. 并行调用多个 Worker
 * 3. 容错：单个 Worker 失败不影响整体
 * 4. 汇总所有结果
 */
@Component
public class OrchestratorAgent {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final List<Agent> workers;
    private static final long TIMEOUT_MS = 15000;

    public OrchestratorAgent() {
        this.workers = List.of(
                new WeatherWorker(),
                new CalculatorWorker(),
                new SearchWorker(),
                new NoteWorker(),
                new TimeWorker()
        );
        log.info("=== {} 个 Agent 就绪 ===", workers.size());
        workers.forEach(w -> log.info("  Agent: {}", w.getName()));
    }

    /**
     * 处理用户请求
     */
    public OrchestrationResult process(String userInput) {
        long start = System.currentTimeMillis();
        log.info("🔄 Orchestrator 收到: {}", userInput);

        // Step 1: 路由 —— 分析意图，选择 Worker
        List<Agent> selected = selectWorkers(userInput);
        log.info("🎯 选中 {} 个 Agent: {}", selected.size(),
                selected.stream().map(Agent::getName).collect(Collectors.joining(", ")));

        if (selected.isEmpty()) {
            return new OrchestrationResult(false, "未找到能处理此请求的 Agent",
                    List.of(), System.currentTimeMillis() - start);
        }

        // Step 2: 并行调用 —— 每个 Worker 独立执行
        List<AgentResult> results = executeWorkers(selected, userInput);

        // Step 3: 统计
        long successCount = results.stream().filter(AgentResult::isSuccess).count();
        long failCount = results.size() - successCount;
        long elapsed = System.currentTimeMillis() - start;

        // Step 4: 汇总
        String summary = buildSummary(results);

        log.info("✅ 完成: {} 成功, {} 失败, 耗时 {}ms", successCount, failCount, elapsed);
        return new OrchestrationResult(true, summary, results, elapsed);
    }

    private List<Agent> selectWorkers(String input) {
        List<Agent> selected = new ArrayList<>();
        AgentMessage probe = new AgentMessage("orchestrator", "", AgentMessage.MessageType.REQUEST, input);

        for (Agent worker : workers) {
            if (worker.canHandle(probe)) {
                selected.add(worker);
            }
        }

        // 兜底：没有匹配则用 search
        if (selected.isEmpty()) {
            for (Agent w : workers) {
                if (w.getName().equals("search")) {
                    selected.add(w);
                    break;
                }
            }
        }

        return selected;
    }

    private List<AgentResult> executeWorkers(List<Agent> selected, String input) {
        List<AgentResult> results = new ArrayList<>();
        AgentMessage request = new AgentMessage("orchestrator", "",
                AgentMessage.MessageType.REQUEST, input);

        for (Agent worker : selected) {
            try {
                // 每个 Worker 在独立上下文中执行
                AgentResult result = worker.execute(request);
                results.add(result);
            } catch (Exception e) {
                // 错误隔离：Worker 抛异常只影响自己
                log.warn("⚠️ Agent {} 异常: {}", worker.getName(), e.getMessage());
                results.add(AgentResult.fail(worker.getName(), "异常: " + e.getMessage()));
            }
        }

        return results;
    }

    private String buildSummary(List<AgentResult> results) {
        StringBuilder sb = new StringBuilder();
        for (AgentResult r : results) {
            if (sb.length() > 0) sb.append("\n---\n");
            sb.append("【").append(r.getAgentName()).append("】\n");
            if (r.isSuccess()) {
                sb.append(r.getData());
            } else {
                sb.append("⚠️ ").append(r.getError());
            }
        }
        return sb.toString();
    }
}
