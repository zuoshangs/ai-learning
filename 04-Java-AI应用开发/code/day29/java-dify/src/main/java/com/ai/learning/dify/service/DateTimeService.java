package com.ai.learning.dify.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

@Service
public class DateTimeService {

    public String getInfo(String query) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
        String weekday = now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CHINESE);
        return "当前时间: " + dateStr + " " + weekday;
    }
}