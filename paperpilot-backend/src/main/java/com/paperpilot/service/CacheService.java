package com.paperpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 缓存Key前缀
    private static final String CACHE_ABSTRACT_PREFIX = "ai:cache:abstract:";
    private static final String CACHE_IF_PREFIX = "cache:if:";
    private static final String RATE_LIMIT_PREFIX = "ai:rate:";

    /**
     * 获取缓存的文献分析结果
     */
    public <T> T getCachedAnalysis(String abstractText, Class<T> clazz) {
        try {
            String hash = sha256Hash(abstractText);
            String cacheKey = CACHE_ABSTRACT_PREFIX + hash;
            String cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.debug("Cache hit for abstract hash: {}", hash);
                return objectMapper.readValue(cached, clazz);
            }
        } catch (Exception e) {
            log.error("Failed to get cached analysis", e);
        }
        return null;
    }

    /**
     * 缓存文献分析结果
     */
    public <T> void cacheAnalysis(String abstractText, T result, long days) {
        try {
            String hash = sha256Hash(abstractText);
            String cacheKey = CACHE_ABSTRACT_PREFIX + hash;
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, days, TimeUnit.DAYS);
            log.debug("Cached analysis for abstract hash: {}", hash);
        } catch (Exception e) {
            log.error("Failed to cache analysis", e);
        }
    }

    /**
     * 检查限流
     * @param userId 用户ID
     * @param limit 限制次数
     * @param windowSeconds 时间窗口(秒)
     * @return true-允许通过 false-被限流
     */
    public boolean checkRateLimit(Long userId, int limit, int windowSeconds) {
        String key = RATE_LIMIT_PREFIX + userId + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
        Long current = redisTemplate.opsForValue().increment(key);

        if (current != null && current == 1) {
            // 首次设置过期时间
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return current != null && current <= limit;
    }

    /**
     * 检查IP限制
     * @param ip IP地址
     * @param maxUsers 最大用户数
     * @return true-允许 false-拒绝
     */
    public boolean checkIPLimit(String ip, int maxUsers) {
        String key = "abuse:ip:" + ip + ":" + java.time.LocalDate.now();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }

        return count != null && count <= maxUsers;
    }

    /**
     * 检查行为模式（重复请求检测）
     * @param userId 用户ID
     * @param requestHash 请求哈希
     * @param maxDuplicate 最大重复次数
     * @return true-正常 false-疑似刷量
     */
    public boolean checkBehaviorPattern(Long userId, String requestHash, int maxDuplicate) {
        String key = "abuse:pattern:" + userId;

        // 保留最近10次请求
        redisTemplate.opsForList().leftPush(key, requestHash);
        redisTemplate.opsForList().trim(key, 0, 9);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);

        // 检查重复率
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size < 5) {
            return true; // 数据不足，放行
        }

        Long duplicateCount = redisTemplate.opsForList().range(key, 0, -1).stream()
                .filter(h -> h.equals(requestHash))
                .count();

        return duplicateCount <= maxDuplicate;
    }

    /**
     * 计算MD5哈希
     */
    public String md5Hash(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 计算SHA-256哈希
     */
    public String sha256Hash(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }
}
