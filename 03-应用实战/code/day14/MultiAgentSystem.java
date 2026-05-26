/**
 * MultiAgentSystem.java — 多Agent协作系统
 * 
 * Orchestrator-Worker 模式
 * 并行Worker执行 + 结果合成 + 错误处理
 * 第14天 — Java 版
 */

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MultiAgentSystem {

    // ═══════════════════════════════════════════
    // 数据类
    // ═══════════════════════════════════════════

    static class AgentMessage {
        String sender, receiver, msgType, taskId;
        Map<String, Object> content;
        long timestamp = System.currentTimeMillis();

        AgentMessage(String sender, String receiver, String msgType,
                     String taskId, Map<String, Object> content) {
            this.sender = sender; this.receiver = receiver;
            this.msgType = msgType; this.taskId = taskId;
            this.content = content;
        }
    }

    static class SubTask {
        String id, workerType, query, instructions, status = "pending";
        Map<String, Object> result;
        String error;
        long createdAt = System.currentTimeMillis();
        Long completedAt;

        SubTask(String id, String workerType, String query, String instructions) {
            this.id = id; this.workerType = workerType;
            this.query = query; this.instructions = instructions;
        }
    }

    // ═══════════════════════════════════════════
    // Worker 接口
    // ═══════════════════════════════════════════

    interface Worker {
        AgentMessage execute(SubTask task);
        String getName();
    }

    // ═══════════════════════════════════════════
    // 研究员 Worker
    // ═══════════════════════════════════════════

    static class ResearchWorker implements Worker {
        @Override public String getName() { return "research_worker"; }

        @Override
        public AgentMessage execute(SubTask task) {
            // 模拟搜索 + LLM整理
            Map<String, Object> notes = new LinkedHashMap<>();
            notes.put("summary", "关于'" + task.query + "'的研究发现...");
            notes.put("key_findings", List.of(
                Map.of("finding", task.query + "领域正在快速发展",
                       "confidence", "high"),
                Map.of("finding", "多家公司已加大投入",
                       "confidence", "medium")
            ));
            notes.put("sources_count", 3);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("notes", notes);
            content.put("worker_type", "research");
            content.put("sources", List.of(
                Map.of("url", "https://research.example.com/" + task.query),
                Map.of("url", "https://news.example.com/" + task.query)
            ));

            return new AgentMessage("research_worker", "orchestrator",
                                    "result", task.id, content);
        }
    }

    // ═══════════════════════════════════════════
    // 分析师 Worker
    // ═══════════════════════════════════════════

    static class AnalyzeWorker implements Worker {
        @Override public String getName() { return "analyze_worker"; }

        @Override
        public AgentMessage execute(SubTask task) {
            Map<String, Object> analysis = new LinkedHashMap<>();
            analysis.put("analysis_summary", task.query + "的深度分析...");
            analysis.put("key_insights", List.of(
                Map.of("insight", task.query + "将成为关键趋势",
                       "evidence", "市场需求持续增长"),
                Map.of("insight", "技术成熟度快速提升",
                       "evidence", "多家企业已落地应用")
            ));
            analysis.put("trends", List.of("技术融合加速", "应用场景扩展"));
            analysis.put("recommendations",
                List.of("关注" + task.query + "生态发展", "早期投入布局"));

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("analysis", analysis);
            content.put("worker_type", "analyze");

            return new AgentMessage("analyze_worker", "orchestrator",
                                    "result", task.id, content);
        }
    }

    // ═══════════════════════════════════════════
    // 审查员 Worker
    // ═══════════════════════════════════════════

    static class ReviewWorker implements Worker {
        @Override public String getName() { return "review_worker"; }

        @Override
        public AgentMessage execute(SubTask task) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("overall_score", 85);
            review.put("verdict", "pass");
            review.put("strengths", List.of("分析全面", "数据支撑充分"));
            review.put("issues", List.of(
                Map.of("severity", "minor",
                       "description", "部分来源需进一步验证")
            ));
            review.put("suggestions", List.of("补充更多一手数据"));

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("review", review);
            content.put("worker_type", "review");

            return new AgentMessage("review_worker", "orchestrator",
                                    "result", task.id, content);
        }
    }

    // ═══════════════════════════════════════════
    // 任务分解器
    // ═══════════════════════════════════════════

    static class TaskDecomposer {
        List<SubTask> decompose(String query) {
            String q = query.toLowerCase();
            
            if (q.contains("什么是") || q.contains("简单") || q.contains("快速")) {
                return List.of(new SubTask("analyze", "analyze_worker",
                    query, "用通俗语言解释，200字以内"));
            }

            return List.of(
                new SubTask("research", "research_worker",
                    query, "收集相关信息"),
                new SubTask("analyze", "analyze_worker",
                    query, "深度分析"),
                new SubTask("review", "review_worker",
                    query, "质量审查")
            );
        }
    }

    // ═══════════════════════════════════════════
    // 结果合成器
    // ═══════════════════════════════════════════

    static class ResultSynthesizer {
        String synthesize(String query, Map<String, AgentMessage> results,
                          List<String> errors) {
            StringBuilder sb = new StringBuilder();

            sb.append("多Agent研究报告: ").append(query).append("\n");
            sb.append("生成时间: ").append(LocalDateTime.now()).append("\n\n");

            if (!errors.isEmpty()) {
                sb.append("⚠️ 注意事项:\n");
                for (String e : errors) sb.append("- ").append(e).append("\n");
                sb.append("\n");
            }

            results.forEach((taskId, msg) -> {
                String workerType = (String) msg.content.get("worker_type");
                sb.append(switch (workerType) {
                    case "research" -> formatResearch(msg);
                    case "analyze" -> formatAnalysis(msg);
                    case "review" -> formatReview(msg);
                    default -> "";
                });
            });

            return sb.toString();
        }

        String formatResearch(AgentMessage msg) {
            @SuppressWarnings("unchecked")
            var notes = (Map<String, Object>) msg.content.get("notes");
            StringBuilder sb = new StringBuilder("📖 研究发现\n");
            sb.append(notes.get("summary")).append("\n\n");

            @SuppressWarnings("unchecked")
            var findings = (List<Map<String, String>>) notes.get("key_findings");
            for (var f : findings) {
                sb.append("- ✅ ").append(f.get("finding")).append("\n");
            }
            return sb.append("\n").toString();
        }

        String formatAnalysis(AgentMessage msg) {
            @SuppressWarnings("unchecked")
            var analysis = (Map<String, Object>) msg.content.get("analysis");
            StringBuilder sb = new StringBuilder("📊 深度分析\n");
            sb.append(analysis.get("analysis_summary")).append("\n\n");

            @SuppressWarnings("unchecked")
            var insights = (List<Map<String, String>>) analysis.get("key_insights");
            for (var i : insights) {
                sb.append("💡 ").append(i.get("insight")).append("\n");
            }

            @SuppressWarnings("unchecked")
            var recommendations = (List<String>) analysis.get("recommendations");
            if (recommendations != null) {
                sb.append("\n建议:\n");
                for (String r : recommendations) sb.append("- ✨ ").append(r).append("\n");
            }
            return sb.append("\n").toString();
        }

        String formatReview(AgentMessage msg) {
            @SuppressWarnings("unchecked")
            var review = (Map<String, Object>) msg.content.get("review");
            return String.format("✅ 质量审查 (得分: %s/100)\n结论: %s\n\n",
                review.get("overall_score"), review.get("verdict"));
        }
    }

    // ═══════════════════════════════════════════
    // Orchestrator
    // ═══════════════════════════════════════════

    static class Orchestrator {
        Map<String, Worker> workers = Map.of(
            "research_worker", new ResearchWorker(),
            "analyze_worker", new AnalyzeWorker(),
            "review_worker", new ReviewWorker()
        );
        TaskDecomposer decomposer = new TaskDecomposer();
        ResultSynthesizer synthesizer = new ResultSynthesizer();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        Map<String, AgentMessage> results = new ConcurrentHashMap<>();
        List<String> errors = new CopyOnWriteArrayList<>();

        String processRequest(String query) {
            results.clear();
            errors.clear();
            System.out.println("\n📝 请求: " + query);

            // Step 1: 分解
            System.out.println("🔨 分解任务...");
            List<SubTask> tasks = decomposer.decompose(query);
            System.out.println("   → " + tasks.size() + " 个子任务");

            // Step 2: 并行执行
            System.out.println("🚀 并行执行...");
            List<Future<AgentMessage>> futures = new ArrayList<>();
            for (SubTask task : tasks) {
                Worker worker = workers.get(task.workerType);
                if (worker == null) {
                    errors.add("未知Worker: " + task.workerType);
                    continue;
                }
                futures.add(executor.submit(() -> {
                    System.out.println("  ▶ " + task.workerType);
                    AgentMessage msg = worker.execute(task);
                    System.out.println("  ✅ " + task.workerType + " 完成");
                    return msg;
                }));
            }

            for (Future<AgentMessage> f : futures) {
                try {
                    AgentMessage msg = f.get(30, TimeUnit.SECONDS);
                    results.put(msg.taskId, msg);
                } catch (Exception e) {
                    errors.add("执行失败: " + e.getMessage());
                }
            }

            // Step 3: 合成
            System.out.println("📋 合成报告...");
            return synthesizer.synthesize(query, results, errors);
        }

        void shutdown() { executor.shutdown(); }
    }

    // ═══════════════════════════════════════════
    // 主入口
    // ═══════════════════════════════════════════

    public static void main(String[] args) {
        Orchestrator orch = new Orchestrator();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=".repeat(55));
        System.out.println("  🤝 多Agent研究报告生成系统 (Java版)");
        System.out.println("  Orchestrator + Research + Analyze + Review");
        System.out.println("=".repeat(55));

        System.out.println("\n💡 试试: \"分析AI发展趋势\" \"对比Python和Java\"");

        while (true) {
            System.out.print("\n🧑 你: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println("👋 再见！");
                break;
            }

            String report = orch.processRequest(input);
            System.out.println("\n📋 最终报告:\n" + "-".repeat(40));
            System.out.println(report);
            System.out.println("-".repeat(40));
        }

        orch.shutdown();
        scanner.close();
    }
}
