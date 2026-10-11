package com.clinica.turnos.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory rate limit by key — C-03 D-06.
 * Bucket 5/60s greedy refill.
 */
@Service
public class RateLimitService {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final int windowSeconds;

    public RateLimitService(
            @Value("${app.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.login-rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(maxAttempts, io.github.bucket4j.Refill.greedy(maxAttempts, Duration.ofSeconds(windowSeconds))))
                .build());
    }

    /**
     * Tries to consume 1 token; returns true if allowed, false if limited.
     */
    public boolean tryConsume(String key) {
        return resolveBucket(key).tryConsume(1);
    }

    public void refund(String key) {
        resolveBucket(key).addTokens(1);
    }

    /**
     * Key normalized: lowercased username + "|" + ip.
     */
    public String keyFor(String ip, String username) {
        String normUser = username == null ? "" : username.trim().toLowerCase();
        String normIp = ip == null ? "unknown" : ip.trim();
        return normUser + "|" + normIp;
    }

    public long availableTokens(String key) {
        return resolveBucket(key).getAvailableTokens();
    }

    public void clear() {
        buckets.clear();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }
}
