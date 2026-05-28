package com.ai.learning.dify.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class WeatherService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String getWeather(String city) throws Exception {
        // 使用 wttr.in 查询天气（无需 API Key）
        String url = "https://wttr.in/" + java.net.URLEncoder.encode(city, "UTF-8") + "?format=%C+%t+%w+%h";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return city + "天气: " + resp.body().trim();
    }
}