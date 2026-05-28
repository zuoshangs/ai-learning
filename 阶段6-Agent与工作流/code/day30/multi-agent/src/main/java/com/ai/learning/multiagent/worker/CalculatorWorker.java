package com.ai.learning.multiagent.worker;

import com.ai.learning.multiagent.core.*;
import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CalculatorWorker implements Agent {

    @Override
    public String getName() { return "calculator"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String p = message.getPayload().trim();
        return p.matches(".*\\d+.*[+\\-*/].*") || p.matches("\\d+\\.?\\d*");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            String expr = extractExpression(message.getPayload());
            double result = evaluate(expr);
            return AgentResult.ok(getName(), expr + " = " + formatResult(result));
        } catch (Exception e) {
            return AgentResult.fail(getName(), "计算失败: " + e.getMessage());
        }
    }

    private String extractExpression(String text) {
        // 提取数字和运算符组成的表达式
        Pattern p = Pattern.compile("-?\\d+(\\.\\d+)?\\s*[+\\-*/]\\s*-?\\d+(\\.\\d+)?(\\s*[+\\-*/]\\s*-?\\d+(\\.\\d+)?)*");
        Matcher m = p.matcher(text.replaceAll("×", "*").replaceAll("÷", "/").replaceAll("\\s+", ""));
        if (m.find()) return m.group();
        // 纯数字
        Pattern p2 = Pattern.compile("-?\\d+\\.?\\d*");
        Matcher m2 = p2.matcher(text.trim());
        return m2.find() ? m2.group() : text;
    }

    private double evaluate(String expr) {
        // 简单四则运算解析器（支持 + - * /）
        expr = expr.replaceAll("\\s+", "");

        // 先处理乘除
        Pattern md = Pattern.compile("(-?\\d+(\\.\\d+)?)\\s*([*/])\\s*(-?\\d+(\\.\\d+)?)");
        while (expr.contains("*") || expr.contains("/")) {
            Matcher m = md.matcher(expr);
            if (!m.find()) break;
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(4));
            double r = m.group(3).equals("*") ? a * b : a / b;
            expr = expr.substring(0, m.start()) + formatResult(r) + expr.substring(m.end());
        }

        // 再处理加减
        Pattern as = Pattern.compile("(-?\\d+(\\.\\d+)?)\\s*([+\\-])\\s*(-?\\d+(\\.\\d+)?)");
        while (expr.contains("+") || (expr.contains("-") && expr.lastIndexOf("-") > 0)) {
            Matcher m = as.matcher(expr);
            if (!m.find()) break;
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(4));
            double r = m.group(3).equals("+") ? a + b : a - b;
            expr = expr.substring(0, m.start()) + formatResult(r) + expr.substring(m.end());
        }

        return Double.parseDouble(expr);
    }

    private String formatResult(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.format("%.2f", d);
    }
}
