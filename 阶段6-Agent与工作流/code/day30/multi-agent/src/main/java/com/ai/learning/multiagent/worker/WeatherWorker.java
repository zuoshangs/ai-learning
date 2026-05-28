package com.ai.learning.multiagent.worker;

import com.ai.learning.multiagent.core.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class WeatherWorker implements Agent {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String getName() { return "weather"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String payload = message.getPayload().toLowerCase();
        return payload.contains("天气") || payload.contains("weather")
                || payload.contains("气温") || payload.contains("温度");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            String city = extractCity(message.getPayload());
            String url = "https://wttr.in/" + city
                    + "?format=%25C+%25t+%25w+%25h";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return AgentResult.ok(getName(), city + "天气: " + resp.body().trim());
        } catch (Exception e) {
            return AgentResult.fail(getName(), "天气查询失败: " + e.getMessage());
        }
    }

    private String extractCity(String text) {
        int idx = text.indexOf("天气");
        if (idx > 0) {
            String before = text.substring(0, idx).trim();
            String[] parts = before.split("\\s+");
            return parts[parts.length - 1];
        }
        return "北京";
    }
}