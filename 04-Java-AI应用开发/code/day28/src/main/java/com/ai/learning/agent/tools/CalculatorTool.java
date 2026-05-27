package com.ai.learning.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * 计算器工具 — 支持四则运算和数学表达式
 * 使用 javax.script.ScriptEngine (Nashorn/ GraalVM) 安全执行数学表达式
 */
@Component
public class CalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);
    private final ScriptEngine engine;

    public CalculatorTool() {
        ScriptEngine e = new ScriptEngineManager().getEngineByName("JavaScript");
        if (e == null) {
            e = new ScriptEngineManager().getEngineByName("nashorn");
        }
        this.engine = e;
    }

    /**
     * 执行数学计算
     * @param expression 数学表达式，如 "25 * 9 / 5 + 32"
     * @return 计算结果字符串
     */
    public String calculate(String expression) {
        log.info("计算表达式: {}", expression);
        try {
            // 安全校验：只允许数学字符
            if (!isSafeExpression(expression)) {
                return "错误：表达式包含不允许的字符，仅支持数字、运算符和括号";
            }
            Object result = engine.eval(expression);
            log.info("计算结果: {} = {}", expression, result);
            return String.valueOf(result);
        } catch (ScriptException e) {
            log.error("计算失败: {}", e.getMessage());
            return "计算错误: " + e.getMessage();
        }
    }

    /**
     * 温度转换专用：摄氏度转华氏度
     */
    public String celsiusToFahrenheit(double celsius) {
        double fahrenheit = celsius * 9.0 / 5.0 + 32;
        String result = String.format("%.1f°C = %.1f°F", celsius, fahrenheit);
        log.info("温度转换: {}", result);
        return result;
    }

    /**
     * 安全校验：仅允许数学表达式字符
     */
    private boolean isSafeExpression(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            return false;
        }
        return expr.matches("[0-9+\\-*/().%^, \\t\\n\\r]+");
    }

    /**
     * 获取工具描述（供LLM使用）
     */
    public static String getToolDescription() {
        return """
            ## CalculatorTool
            - 功能：执行数学计算
            - 用法：calculate("表达式") — 如 calculate("25 * 9 / 5 + 32")
            - 也支持：celsiusToFahrenheit(数值) — 摄氏度转华氏度
            """;
    }
}
