package com.ai.learning.multiagent.worker;

import com.ai.learning.multiagent.core.*;
import org.springframework.stereotype.Component;

@Component
public class SearchWorker implements Agent {
    @Override
    public String getName() { return "search"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String p = message.getPayload().toLowerCase();
        return p.contains("搜索") || p.contains("查找") || p.contains("查询")
                || p.contains("什么是") || p.contains("search");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            return AgentResult.ok(getName(),
                    "【搜索 Agent】已收到查询: " + message.getPayload()
                    + "\n（实际部署时接入百度搜索/DuckDuckGo API）");
        } catch (Exception e) {
            return AgentResult.fail(getName(), "搜索失败: " + e.getMessage());
        }
    }
}