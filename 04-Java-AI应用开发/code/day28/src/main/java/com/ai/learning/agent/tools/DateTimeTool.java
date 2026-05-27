package com.ai.learning.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 日期时间工具 — 获取当前日期时间、计算时间差等
 */
@Component
public class DateTimeTool {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTool.class);

    /**
     * 获取当前日期时间
     */
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        String result = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("当前日期时间: {}", result);
        return "当前日期时间：" + result;
    }

    /**
     * 获取当前日期
     */
    public String getCurrentDate() {
        LocalDate today = LocalDate.now();
        String result = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)"));
        log.info("当前日期: {}", result);
        return "当前日期：" + result;
    }

    /**
     * 获取当前时间
     */
    public String getCurrentTime() {
        LocalTime now = LocalTime.now();
        String result = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.info("当前时间: {}", result);
        return "当前时间：" + result;
    }

    /**
     * 计算两个日期之间的天数差
     */
    public String daysBetween(String date1, String date2) {
        try {
            LocalDate d1 = LocalDate.parse(date1);
            LocalDate d2 = LocalDate.parse(date2);
            long days = ChronoUnit.DAYS.between(d1, d2);
            String result = String.format("%s 到 %s 相差 %d 天", date1, date2, days);
            log.info("日期差: {}", result);
            return result;
        } catch (Exception e) {
            log.error("日期计算失败: {}", e.getMessage());
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    /**
     * 获取工具描述
     */
    public static String getToolDescription() {
        return """
            ## DateTimeTool
            - 功能：获取当前日期时间、计算日期差
            - 用法：
              • getCurrentDateTime()
              • getCurrentDate()
              • getCurrentTime()
              • daysBetween("2024-01-01", "2024-12-31")
            """;
    }
}
