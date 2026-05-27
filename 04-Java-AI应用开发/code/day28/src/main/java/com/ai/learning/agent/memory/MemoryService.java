package com.ai.learning.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆服务 — 短期记忆（InMemory）+ 长期记忆（PostgreSQL）
 * 自动管理 token 上限，超出时裁剪最旧消息
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    // 短期记忆存储（当前会话 token 上限约 4000 tokens）
    private static final int MAX_SHORT_TERM_TOKENS = 4000;
    private final Map<String, ConversationMemory> shortTermMemory = new ConcurrentHashMap<>();

    // 长期记忆 — PostgreSQL
    private final JdbcTemplate jdbcTemplate;
    private boolean dbAvailable = false;

    public MemoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initDatabase();
    }

    /** 初始化数据库表 */
    private void initDatabase() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversation_memory (
                    id SERIAL PRIMARY KEY,
                    session_id VARCHAR(255) NOT NULL,
                    role VARCHAR(50) NOT NULL,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_conversation_memory_session
                ON conversation_memory(session_id, created_at)
            """);
            dbAvailable = true;
            log.info("PostgreSQL 长期记忆就绪");
        } catch (Exception e) {
            log.warn("PostgreSQL 不可用，仅使用短期记忆: {}", e.getMessage());
            dbAvailable = false;
        }
    }

    // ========== 短期记忆 ==========

    /** 获取或创建短期会话 */
    public ConversationMemory getOrCreateShortTerm(String sessionId) {
        return shortTermMemory.computeIfAbsent(sessionId, k -> {
            log.info("创建新短期会话: {}", sessionId);
            ConversationMemory mem = new ConversationMemory(sessionId);
            mem.addMessage("system", "你是一个智能助手，可以使用多种工具来帮助用户完成任务。"
                + "可用工具：天气查询、数学计算、日期时间、文档读写。");
            return mem;
        });
    }

    /** 添加消息到短期记忆，自动裁剪 */
    public void addToShortTerm(String sessionId, String role, String content) {
        ConversationMemory mem = getOrCreateShortTerm(sessionId);
        mem.addMessage(role, content);
        trimToTokenLimit(mem);
    }

    /** 获取短期消息列表 */
    public List<ConversationMemory.Message> getShortTermMessages(String sessionId) {
        ConversationMemory mem = shortTermMemory.get(sessionId);
        if (mem == null) {
            return getOrCreateShortTerm(sessionId).getMessages();
        }
        return mem.getMessages();
    }

    /** 清空短期记忆 */
    public void clearShortTerm(String sessionId) {
        shortTermMemory.remove(sessionId);
        log.info("清空短期会话: {}", sessionId);
    }

    /** 裁剪消息直到 token 数低于上限（粗略估算） */
    private void trimToTokenLimit(ConversationMemory mem) {
        List<ConversationMemory.Message> msgs = mem.getMessages();
        int totalTokens = estimateTokens(msgs);

        while (totalTokens > MAX_SHORT_TERM_TOKENS && msgs.size() > 2) {
            // 保留系统消息（第一条）和最新的消息
            ConversationMemory.Message removed = msgs.remove(1); // 删除第一条用户/助手消息
            totalTokens = estimateTokens(msgs);
            log.debug("裁剪消息: {}... (剩余 tokens: {})",
                removed.getContent().substring(0, Math.min(30, removed.getContent().length())),
                totalTokens);
        }
    }

    /** 粗略估算 token 数（中英文混合约 1.5 chars/token） */
    private int estimateTokens(List<ConversationMemory.Message> messages) {
        int totalChars = 0;
        for (ConversationMemory.Message msg : messages) {
            totalChars += msg.getContent().length();
        }
        return (int) (totalChars / 1.5);
    }

    private int estimateTokens(ConversationMemory.Message msg) {
        return (int) (msg.getContent().length() / 1.5);
    }

    // ========== 长期记忆 ==========

    /** 保存消息到长期记忆（PostgreSQL） */
    public void saveToLongTerm(String sessionId, String role, String content) {
        if (!dbAvailable) return;
        try {
            jdbcTemplate.update(
                "INSERT INTO conversation_memory (session_id, role, content) VALUES (?, ?, ?)",
                sessionId, role, content
            );
        } catch (Exception e) {
            log.error("保存长期记忆失败: {}", e.getMessage());
        }
    }

    /** 获取长期记忆历史 */
    public List<ConversationMemory.Message> getLongTermHistory(String sessionId) {
        if (!dbAvailable) return Collections.emptyList();
        try {
            return jdbcTemplate.query(
                "SELECT role, content FROM conversation_memory WHERE session_id = ? ORDER BY created_at ASC",
                new RowMapper<ConversationMemory.Message>() {
                    @Override
                    public ConversationMemory.Message mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return new ConversationMemory.Message(
                            rs.getString("role"),
                            rs.getString("content")
                        );
                    }
                },
                sessionId
            );
        } catch (Exception e) {
            log.error("读取长期记忆失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 获取所有会话 ID */
    public List<String> getAllSessionIds() {
        if (!dbAvailable) return new ArrayList<>(shortTermMemory.keySet());
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT session_id FROM conversation_memory ORDER BY MAX(created_at) DESC",
                String.class
            );
        } catch (Exception e) {
            return new ArrayList<>(shortTermMemory.keySet());
        }
    }

    // ========== 综合接口 ==========

    /** 获取完整上下文（短期+长期合并，去重） */
    public List<ConversationMemory.Message> getFullContext(String sessionId) {
        // 优先返回短期（包含最全的当前会话信息）
        List<ConversationMemory.Message> shortTerm = getShortTermMessages(sessionId);
        if (shortTerm.size() > 3) {
            return shortTerm; // 短期已有足够上下文
        }

        // 短期太少则合并长期
        List<ConversationMemory.Message> longTerm = getLongTermHistory(sessionId);
        Set<String> existing = new HashSet<>();
        for (ConversationMemory.Message m : shortTerm) {
            existing.add(m.getRole() + ":" + m.getContent());
        }

        List<ConversationMemory.Message> combined = new ArrayList<>(longTerm);
        for (ConversationMemory.Message m : shortTerm) {
            String key = m.getRole() + ":" + m.getContent();
            if (!existing.contains(key)) {
                combined.add(m);
            }
        }
        return combined;
    }

    /** 保存到短期+长期 */
    public void saveMessage(String sessionId, String role, String content) {
        addToShortTerm(sessionId, role, content);
        saveToLongTerm(sessionId, role, content);
    }
}
