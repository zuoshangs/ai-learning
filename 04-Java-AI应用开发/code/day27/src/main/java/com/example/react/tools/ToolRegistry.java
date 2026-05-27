package com.example.react.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 工具注册中心 — 手动管理工具的注册、查找和执行。
 * <p>
 * 与 Day 19 的 @Tool + MethodToolCallbackProvider 自动注册不同，
 * 这里我们手动将每个工具的名称、描述和执行函数注册到一个 Map 中，
 * 由 ReActAgent 手动调度。
 */
@Component
public class ToolRegistry {

    /**
     * 工具注册表：名称 → (描述, 执行函数)
     */
    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();

    public ToolRegistry(
            CalculatorTool calculator,
            WeatherTool weather,
            DateTimeTool dateTime) {
        // 注册计算器工具
        register("calculate",
                "执行数学计算，支持加(+)、减(-)、乘(*)、除(/)，传入表达式如 '123 * 456'",
                args -> calculator.calculate(args.get("expression").asText()));

        register("convert_unit",
                "单位换算，支持长度(米/千米/英尺)、重量(克/千克/斤/磅)、温度(摄氏/华氏)",
                args -> calculator.convertUnit(
                        args.get("value").asText(),
                        args.get("from_unit").asText(),
                        args.get("to_unit").asText()));

        // 注册天气工具
        register("get_weather",
                "查询指定城市的当前天气情况，包括温度、天气状况、湿度和风力",
                args -> weather.getWeather(args.get("city").asText()));

        register("get_forecast",
                "获取指定城市未来3天的天气预报，包含每天的最高/最低温度、天气状况",
                args -> weather.getForecast(args.get("city").asText()));

        // 注册日期时间工具
        register("get_current_time",
                "获取当前的日期和时间（无需参数）",
                args -> dateTime.getCurrentTime());
    }

    /**
     * 注册一个工具
     */
    public void register(String name, String description, Function<com.fasterxml.jackson.databind.JsonNode, String> executor) {
        tools.put(name, new ToolEntry(name, description, executor));
    }

    /**
     * 根据名称查找工具
     */
    public Optional<ToolEntry> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有工具的描述（用于生成系统提示词）
     */
    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用工具：\n");
        for (ToolEntry entry : tools.values()) {
            sb.append("- ").append(entry.name).append("(");
            // 描述参数
            switch (entry.name) {
                case "calculate" -> sb.append("expression: str");
                case "convert_unit" -> sb.append("value: str, from_unit: str, to_unit: str");
                case "get_weather" -> sb.append("city: str");
                case "get_forecast" -> sb.append("city: str");
                case "get_current_time" -> sb.append("");
            }
            sb.append("): ").append(entry.description).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取所有工具名称列表
     */
    public String[] getToolNames() {
        return tools.keySet().toArray(new String[0]);
    }

    /**
     * 工具条目 — 名称、描述、执行函数
     */
    public record ToolEntry(String name, String description,
                            Function<com.fasterxml.jackson.databind.JsonNode, String> executor) {
    }
}
