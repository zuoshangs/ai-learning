package com.ai.learning.knowledge.ingestion;

import com.ai.learning.knowledge.model.DocumentChunk;
import com.ai.learning.knowledge.model.KnowledgeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步文档摄取管线
 *
 * 工作流：
 *   收到文档 → 入队 → 异步切分 → 异步向量化入库
 *
 * 使用内存队列 + ScheduledExecutor 实现异步处理
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    /** 内存队列 */
    private final BlockingQueue<IngestionTask> queue = new LinkedBlockingQueue<>(100);

    /** 任务 ID 生成器 */
    private final AtomicInteger taskIdGen = new AtomicInteger(0);

    /** 任务状态表 */
    private final ConcurrentHashMap<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();

    /** 处理器线程 */
    private final ExecutorService processor;

    /** 统计 */
    private final AtomicInteger totalIngested = new AtomicInteger(0);

    public DocumentIngestionService(ChunkingService chunkingService,
                                    EmbeddingService embeddingService) {
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.processor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ingestion-processor");
            t.setDaemon(true);
            return t;
        });
        startProcessor();
    }

    /**
     * 提交文档摄入任务（异步）
     */
    public String ingestAsync(KnowledgeDocument doc) {
        String taskId = "task-" + taskIdGen.incrementAndGet();
        TaskStatus status = new TaskStatus(taskId, "queued", doc.getId());
        taskStatuses.put(taskId, status);

        queue.offer(new IngestionTask(taskId, doc));
        log.info("📥 提交摄入任务 [{}]: {} (队列长度={})", taskId, doc.getTitle(), queue.size());
        return taskId;
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getStatus(String taskId) {
        return taskStatuses.get(taskId);
    }

    /**
     * 获取总摄入统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalIngested", totalIngested.get());
        stats.put("queueSize", queue.size());
        stats.put("processing", taskStatuses.values().stream()
            .filter(s -> "processing".equals(s.status)).count());
        stats.put("completed", taskStatuses.values().stream()
            .filter(s -> "completed".equals(s.status)).count());
        stats.put("failed", taskStatuses.values().stream()
            .filter(s -> "failed".equals(s.status)).count());
        return stats;
    }

    // ===== 内部 =====

    private void startProcessor() {
        processor.submit(() -> {
            while (true) {
                try {
                    IngestionTask task = queue.poll(5, TimeUnit.SECONDS);
                    if (task == null) continue;

                    processTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("摄入处理异常", e);
                }
            }
        });
        log.info("🔄 摄入处理器已启动");
    }

    private void processTask(IngestionTask task) {
        TaskStatus status = taskStatuses.get(task.taskId);
        status.status = "processing";
        log.info("⚙️  处理摄入任务: {}", task.doc.getTitle());

        try {
            // 1. 切分
            List<DocumentChunk> chunks = chunkingService.chunkDocument(task.doc);
            if (chunks.isEmpty()) {
                status.status = "completed";
                status.totalChunks = 0;
                return;
            }

            // 2. 入库
            int stored = embeddingService.storeChunks(chunks);

            // 3. 更新状态
            task.doc.setTotalChunks(stored);
            status.status = "completed";
            status.totalChunks = stored;
            totalIngested.incrementAndGet();

            log.info("✅ [{}] 摄入完成: {} 块 → PgVector", task.taskId, stored);

        } catch (Exception e) {
            status.status = "failed";
            status.error = e.getMessage();
            log.error("❌ [{}] 摄入失败: {}", task.taskId, e.getMessage(), e);
        }
    }

    // ===== 内部类 =====

    private record IngestionTask(String taskId, KnowledgeDocument doc) {}

    public static class TaskStatus {
        public final String taskId;
        public volatile String status;   // queued / processing / completed / failed
        public final String docId;
        public int totalChunks;
        public String error;

        TaskStatus(String taskId, String status, String docId) {
            this.taskId = taskId;
            this.status = status;
            this.docId = docId;
        }
    }
}
