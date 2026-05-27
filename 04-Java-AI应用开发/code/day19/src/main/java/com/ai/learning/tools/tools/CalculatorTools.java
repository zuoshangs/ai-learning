package com.ai.learning.tools.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计算器工具 — 大模型不擅长精确数学运算
 * 
 * 虽然 LLM 能做简单算术，但复杂计算（大数乘法、除法、混合运算）
 * 经常出错。通过工具调用将计算委托给 Java 精确计算。
 */
@Component
public class CalculatorTools {

    /**
     * 执行数学计算 — 支持加、减、乘、除
     * 
     * @param expression 数学表达式，格式: "数字 运算符 数字"
     *                   示例: "123 * 456", "100 + 200", "500 / 3"
     * @return 计算结果（除法保留2位小数）
     */
    @Tool(description = "执行数学计算，支持加(+)、减(-)、乘(*)、除(/)，传入格式如 '123 * 456'")
    public String calculate(String expression) {
        try {
            // 统一分隔符：支持多种写法
            String cleaned = expression
                .replace("×", "*")
                .replace("x", "*")
                .replace("X", "*")
                .replace("÷", "/")
                .replace("＋", "+")
                .replace("－", "-")
                .trim();
            
            // 解析表达式: "数字 运算符 数字"
            String[] parts;
            String operator = "";
            
            if (cleaned.contains("+")) {
                parts = cleaned.split("\\+", 2);
                operator = "+";
            } else if (cleaned.contains("-")) {
                parts = cleaned.split("-", 2);
                operator = "-";
            } else if (cleaned.contains("*")) {
                parts = cleaned.split("\\*", 2);
                operator = "*";
            } else if (cleaned.contains("/")) {
                parts = cleaned.split("/", 2);
                operator = "/";
            } else {
                return "❌ 无法识别的运算符，请使用 +、-、*、/";
            }
            
            if (parts.length < 2) {
                return "❌ 表达式格式错误，正确格式如 '123 * 456'";
            }
            
            BigDecimal left = new BigDecimal(parts[0].trim());
            BigDecimal right = new BigDecimal(parts[1].trim());
            BigDecimal result;
            
            switch (operator) {
                case "+" -> result = left.add(right);
                case "-" -> result = left.subtract(right);
                case "*" -> result = left.multiply(right);
                case "/" -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        return "❌ 除数不能为零";
                    }
                    result = left.divide(right, 10, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                }
                default -> throw new IllegalStateException("Unexpected operator: " + operator);
            }
            
            // 格式化输出：整数不留小数位
            String resultStr = result.toPlainString();
            
            return """
                📊 计算结果
                ──────────
                %s %s %s = %s
                """.formatted(
                    left.stripTrailingZeros().toPlainString(),
                    operator,
                    right.stripTrailingZeros().toPlainString(),
                    resultStr
                );
                
        } catch (NumberFormatException e) {
            return "❌ 数字格式错误，请确保输入是有效的数字";
        } catch (Exception e) {
            return "❌ 计算出错：" + e.getMessage();
        }
    }

    /**
     * 执行单位换算
     */
    @Tool(description = "单位换算，支持长度(米/千米/英尺)、重量(克/千克/斤/磅)、温度(摄氏/华氏)")
    public String convertUnit(String value, String fromUnit, String toUnit) {
        try {
            double val = Double.parseDouble(value);
            double result;
            String resultUnit;
            
            // 统一转标准单位再转目标
            double standard; // 转成SI标准值
            
            standard = switch (fromUnit) {
                // 长度
                case "米", "m" -> val;
                case "千米", "公里", "km" -> val * 1000;
                case "厘米", "cm" -> val / 100;
                case "英尺", "ft" -> val * 0.3048;
                case "英寸", "in" -> val * 0.0254;
                // 重量
                case "克", "g" -> val / 1000;
                case "千克", "公斤", "kg" -> val;
                case "斤" -> val * 0.5;
                case "磅", "lb" -> val * 0.45359237;
                case "吨", "t" -> val * 1000;
                default -> throw new IllegalArgumentException("不支持的单位：" + fromUnit);
            };
            
            result = switch (toUnit) {
                case "米", "m" -> standard;
                case "千米", "公里", "km" -> standard / 1000;
                case "厘米", "cm" -> standard * 100;
                case "英尺", "ft" -> standard / 0.3048;
                case "英寸", "in" -> standard / 0.0254;
                case "克", "g" -> standard * 1000;
                case "千克", "公斤", "kg" -> standard;
                case "斤" -> standard / 0.5;
                case "磅", "lb" -> standard / 0.45359237;
                case "吨", "t" -> standard / 1000;
                default -> throw new IllegalArgumentException("不支持的单位：" + toUnit);
            };
            resultUnit = toUnit;
            
            // 特殊处理温度：华氏↔摄氏
            if ((fromUnit.equals("摄氏度") || fromUnit.equals("℃")) && 
                (toUnit.equals("华氏度") || toUnit.equals("℉"))) {
                result = val * 9 / 5 + 32;
                resultUnit = "℉";
            } else if ((fromUnit.equals("华氏度") || fromUnit.equals("℉")) && 
                       (toUnit.equals("摄氏度") || toUnit.equals("℃"))) {
                result = (val - 32) * 5 / 9;
                resultUnit = "℃";
            }
            
            String formatted = result == Math.floor(result) ? 
                String.format("%.0f", result) : String.format("%.2f", result);
            
            return """
                📐 单位换算结果
                ────────────
                %s %s = %s %s
                """.formatted(value, fromUnit, formatted, resultUnit);
                
        } catch (NumberFormatException e) {
            return "❌ 数值格式错误";
        } catch (Exception e) {
            return "❌ " + e.getMessage();
        }
    }
}
