package com.ai.learning.tools.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具 — 模拟实时天气数据
 * 
 * 大模型无法获取实时信息（训练数据有截止日期），
 * 通过 @Tool 注册后，AI 会在需要时主动调用此方法。
 */
@Component
public class WeatherTools {

    /**
     * 查询指定城市的当前天气情况
     */
    @Tool(description = "查询指定城市的当前天气情况，包括温度、天气状况、湿度和风力")
    public String getWeather(String city) {
        // 模拟数据 — 真实场景可对接第三方天气API
        return switch (city) {
            case "北京", "beijing", "bj" ->
                """
                🌡️ 北京 当前天气
                ────────────────
                天气：☀️ 晴
                温度：25°C（体感 23°C）
                湿度：40%
                风力：北风 2 级
                PM2.5：55（良）
                日出：05:12  日落：19:28
                """;
            case "上海", "shanghai", "sh" ->
                """
                🌡️ 上海 当前天气
                ────────────────
                天气：⛅ 多云
                温度：28°C（体感 30°C）
                湿度：68%
                风力：东南风 3 级
                PM2.5：42（优）
                日出：05:08  日落：19:15
                """;
            case "深圳", "shenzhen", "sz" ->
                """
                🌡️ 深圳 当前天气
                ────────────────
                天气：🌦️ 阵雨
                温度：30°C（体感 34°C）
                湿度：82%
                风力：南风 4 级
                PM2.5：25（优）
                日出：05:55  日落：18:45
                """;
            case "广州", "guangzhou", "gz" ->
                """
                🌡️ 广州 当前天气
                ────────────────
                天气：🌧️ 小雨
                温度：27°C（体感 29°C）
                湿度：78%
                风力：东南风 3 级
                PM2.5：31（优）
                日出：05:50  日落：18:50
                """;
            default ->
                """
                🌡️ %s 当前天气
                ────────────────
                天气：☀️ 晴
                温度：23°C（体感 22°C）
                湿度：50%
                风力：微风
                """.formatted(city);
        };
    }

    /**
     * 获取指定城市未来几天的天气预报
     */
    @Tool(description = "获取指定城市未来3天的天气预报，包含每天的最高/最低温度、天气状况")
    public String getForecast(String city) {
        return switch (city) {
            case "北京", "beijing" ->
                """
                📅 北京 未来3天预报
                ─────────────────
                🌤️ 明天：晴 18°C ~ 27°C
                ⛅ 后天：多云 20°C ~ 25°C
                🌧️ 大后天：小雨 16°C ~ 22°C
                """;
            case "上海", "shanghai" ->
                """
                📅 上海 未来3天预报
                ─────────────────
                ⛅ 明天：多云 22°C ~ 29°C
                🌦️ 后天：阵雨 21°C ~ 26°C
                ☀️ 大后天：晴 19°C ~ 28°C
                """;
            default ->
                """
                📅 %s 未来3天预报
                ─────────────────
                ☀️ 明天：21°C ~ 28°C 晴
                ⛅ 后天：20°C ~ 26°C 多云
                ☀️ 大后天：19°C ~ 27°C 晴
                """.formatted(city);
        };
    }
}
