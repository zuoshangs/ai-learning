package com.ai.learning.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档工具 — 读写临时笔记/文档
 * 支持保存笔记、读取笔记、列出所有笔记
 */
@Component
public class DocumentTool {

    private static final Logger log = LoggerFactory.getLogger(DocumentTool.class);
    private static final Path NOTES_DIR = Path.of(System.getProperty("user.home"), ".agent-notes");

    public DocumentTool() {
        try {
            Files.createDirectories(NOTES_DIR);
            log.info("笔记目录: {}", NOTES_DIR);
        } catch (IOException e) {
            log.error("创建笔记目录失败: {}", e.getMessage());
        }
    }

    /**
     * 保存笔记
     * @param title 笔记标题
     * @param content 笔记内容
     * @return 保存结果
     */
    public String saveNote(String title, String content) {
        try {
            // 标题安全化：去除非法文件名字符
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path filePath = NOTES_DIR.resolve(safeTitle + ".md");

            String noteContent = String.format(""" 
                # %s
                
                创建时间：%s
                ---
                
                %s
                """, title, java.time.LocalDateTime.now(), content);

            Files.writeString(filePath, noteContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("笔记已保存: {}", filePath);
            return "✅ 笔记《" + title + "》已保存至 " + filePath;
        } catch (IOException e) {
            log.error("保存笔记失败: {}", e.getMessage());
            return "❌ 保存笔记失败: " + e.getMessage();
        }
    }

    /**
     * 读取笔记
     * @param title 笔记标题（不含 .md 后缀）
     * @return 笔记内容
     */
    public String readNote(String title) {
        try {
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path filePath = NOTES_DIR.resolve(safeTitle + ".md");

            if (!Files.exists(filePath)) {
                return "❌ 笔记《" + title + "》不存在。可用 listNotes() 查看所有笔记";
            }

            String content = Files.readString(filePath);
            log.info("已读取笔记: {}", filePath);
            return content;
        } catch (IOException e) {
            log.error("读取笔记失败: {}", e.getMessage());
            return "❌ 读取笔记失败: " + e.getMessage();
        }
    }

    /**
     * 列出所有笔记
     * @return 笔记列表
     */
    public String listNotes() {
        try {
            List<Path> notes;
            try (var stream = Files.list(NOTES_DIR)) {
                notes = stream.filter(p -> p.toString().endsWith(".md"))
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .collect(Collectors.toList());
            }

            if (notes.isEmpty()) {
                return "📝 暂无笔记。使用 saveNote(title, content) 创建第一篇笔记";
            }

            Collections.reverse(notes); // 最新的在前
            StringBuilder sb = new StringBuilder("📚 笔记列表（共 " + notes.size() + " 篇）：\n\n");
            for (Path note : notes) {
                String name = note.getFileName().toString().replace(".md", "");
                long size = Files.size(note);
                String modified = Files.getLastModifiedTime(note).toString().substring(0, 19);
                sb.append(String.format("  - 《%s》 (%d 字符, %s)\n", name, size, modified));
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("列出笔记失败: {}", e.getMessage());
            return "❌ 列出笔记失败: " + e.getMessage();
        }
    }

    /**
     * 获取工具描述
     */
    public static String getToolDescription() {
        return """
            ## DocumentTool
            - 功能：读写临时笔记/文档（保存在 ~/.agent-notes/ 目录下）
            - 用法：
              • saveNote("标题", "内容") — 保存笔记
              • readNote("标题") — 读取笔记
              • listNotes() — 列出所有笔记
            """;
    }
}
