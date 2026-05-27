package com.example.react.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具 — 获取当前日期和时间。
 * 无需外部 API，使用 Java LocalDateTime 实现。
 */
@Component
public class DateTimeTool {

    /**
     * 获取当前的日期和时间
     *
     * @return 格式化后的日期时间字符串
     */
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return """
                🕐 当前时间
                ─────────
                日期：%s
                时间：%s
                星期：%s
                """.formatted(
                now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                switch (now.getDayOfWeek()) {
                    case MONDAY -> "一";
                    case TUESDAY -> "二";
                    case WEDNESDAY -> "三";
                    case THURSDAY -> "四";
                    case FRIDAY -> "五";
                    case SATURDAY -> "六";
                    case SUNDAY -> "日";
                }
        );
    }
}
