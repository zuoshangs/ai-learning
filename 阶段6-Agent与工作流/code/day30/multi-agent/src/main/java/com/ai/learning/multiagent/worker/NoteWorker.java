package com.ai.learning.multiagent.worker;

import com.ai.learning.multiagent.core.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NoteWorker implements Agent {
    private final Map<String, String> notes = new LinkedHashMap<>();

    @Override
    public String getName() { return "note"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String p = message.getPayload().toLowerCase();
        return p.contains("笔记") || p.contains("记住") || p.contains("保存")
                || p.contains("记录") || p.contains("note") || p.contains("备忘");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            String p = message.getPayload();
            if (p.contains("记住") || p.contains("保存") || p.contains("记录")) {
                String[] parts = p.split("[\uff1a:\uff0c,]");
                String content = parts.length > 1 ? parts[1].trim() : p;
                String key = "note_" + notes.size();
                notes.put(key, content);
                return AgentResult.ok(getName(), "已保存笔记: " + content);
            } else if (p.contains("列表") || p.contains("所有")) {
                if (notes.isEmpty()) return AgentResult.ok(getName(), "暂无笔记");
                String list = notes.values().stream()
                        .map(v -> "- " + v).collect(Collectors.joining("\n"));
                return AgentResult.ok(getName(), "笔记列表:\n" + list);
            } else {
                return AgentResult.ok(getName(), "当前有 " + notes.size() + " 条笔记");
            }
        } catch (Exception e) {
            return AgentResult.fail(getName(), "笔记操作失败: " + e.getMessage());
        }
    }
}