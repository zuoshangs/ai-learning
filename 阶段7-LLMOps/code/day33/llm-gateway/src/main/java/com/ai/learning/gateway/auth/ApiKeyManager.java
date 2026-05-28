package com.ai.learning.gateway.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key manager.
 * Stores and validates API keys for gateway authentication.
 * Supports different tiers/rate limits per key.
 */
@Component
public class ApiKeyManager {

    /** key -> owner/description */
    private final Map<String, String> keys = new ConcurrentHashMap<>();

    /** key -> tier */
    private final Map<String, String> keyTiers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Built-in test keys (in production, load from DB/Secret Manager)
        addKey("sk-gold-tier", "gold", "VIP user");
        addKey("sk-silver-tier", "silver", "Standard user");
        addKey("sk-free-tier", "free", "Free tier");
    }

    public void addKey(String apiKey, String tier, String owner) {
        keys.put(apiKey, owner);
        keyTiers.put(apiKey, tier);
    }

    public boolean isValid(String apiKey) {
        return keys.containsKey(apiKey);
    }

    public String getOwner(String apiKey) {
        return keys.getOrDefault(apiKey, "unknown");
    }

    public String getTier(String apiKey) {
        return keyTiers.getOrDefault(apiKey, "free");
    }

    public void removeKey(String apiKey) {
        keys.remove(apiKey);
        keyTiers.remove(apiKey);
    }

    public Map<String, String> getAllKeys() {
        return Map.copyOf(keys);
    }

    public void setRateForTier(String tier, int ratePerMinute) {
        // This would update a shared config — for demo, tiers map directly
        // Gold: 1000 rpm, Silver: 200 rpm, Free: 30 rpm
    }

    /** Get rate limit per minute for a key based on its tier. */
    public int getRateLimit(String apiKey) {
        String tier = getTier(apiKey);
        return switch (tier) {
            case "gold" -> 1000;
            case "silver" -> 200;
            case "free" -> 30;
            default -> 60;
        };
    }
}
