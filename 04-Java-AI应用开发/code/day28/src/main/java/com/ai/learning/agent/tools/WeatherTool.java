package com.ai.learning.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 天气查询工具 — 根据城市名称查询实时天气
 * 使用 wttr.in 免费 API（无需 API Key）
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private final RestTemplate restTemplate;

    public WeatherTool() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 查询指定城市的天气
     * @param city 城市名称（中文或英文）
     * @return 天气信息字符串
     */
    @SuppressWarnings("unchecked")
    public String getWeather(String city) {
        log.info("查询天气: {}", city);
        try {
            String url = "https://wttr.in/" + city + "?format=j1";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("current_condition")) {
                return "未能获取到 " + city + " 的天气信息";
            }

            var currentCondition = ((java.util.List<Map<String, Object>>)
                response.get("current_condition")).get(0);

            String temp = currentCondition.get("temp_C") + "°C";
            String feelsLike = currentCondition.get("FeelsLikeC") + "°C";
            String humidity = currentCondition.get("humidity") + "%";
            String desc = ((java.util.List<Map<String, String>>)
                currentCondition.get("weatherDesc")).get(0).get("value");
            String windSpeed = currentCondition.get("windspeedKmph") + " km/h";
            String visibility = currentCondition.get("visibility") + " km";

            String result = String.format("""
                🌤 %s 实时天气：
                - 温度：%s（体感 %s）
                - 天气：%s
                - 湿度：%s
                - 风速：%s
                - 能见度：%s
                """, city, temp, feelsLike, desc, humidity, windSpeed, visibility);

            log.info("天气结果: {}", result.trim());
            return result.trim();
        } catch (Exception e) {
            log.error("天气查询失败: {}", e.getMessage());
            return "天气查询失败: " + e.getMessage() + "（请检查城市名称或网络连接）";
        }
    }

    /**
     * 获取工具描述
     */
    public static String getToolDescription() {
        return """
            ## WeatherTool
            - 功能：查询指定城市的实时天气
            - 用法：getWeather("城市名") — 如 getWeather("Beijing")
            """;
    }
}
