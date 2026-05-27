package com.ai.learning.multiagent.worker;

import com.ai.learning.multiagent.core.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class TimeWorker implements Agent {
    @Override
    public String getName() { return "time"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String p = message.getPayload().toLowerCase();
        return p.contains("时间") || p.contains("日期") || p.contains("现在")
                || p.contains("今天") || p.contains("星期") || p.contains("几号");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            String date = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
            String weekday = now.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL, java.util.Locale.CHINESE);
            return AgentResult.ok(getName(), "当前时间: " + date + " " + weekday);
        } catch (Exception e) {
            return AgentResult.fail(getName(), "时间查询失败: " + e.getMessage());
        }
    }
}