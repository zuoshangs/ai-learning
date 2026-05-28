package com.ai.learning.dify.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemoryNoteService {
    private final Map<String, String> notes = new LinkedHashMap<>();

    public String handle(String action, String params) {
        return switch (action.toLowerCase()) {
            case "save" -> {
                if (params == null || params.isBlank()) yield "请提供笔记内容";
                String[] parts = params.split(":", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : "";
                notes.put(key, value);
                yield "笔记已保存: " + key;
            }
            case "read" -> {
                if (params == null || params.isBlank()) yield "请提供笔记标题";
                String val = notes.get(params.trim());
                yield val != null ? params.trim() + ": " + val : "未找到笔记: " + params;
            }
            case "list" -> {
                if (notes.isEmpty()) yield "暂无笔记";
                yield notes.entrySet().stream()
                        .map(e -> "- " + e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("\n"));
            }
            default -> "未知操作: " + action + "（支持: save/read/list）";
        };
    }
}