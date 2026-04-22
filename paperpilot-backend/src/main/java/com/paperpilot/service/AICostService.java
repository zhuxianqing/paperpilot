package com.paperpilot.service;

import com.paperpilot.entity.AILog;
import com.paperpilot.mapper.AILogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AICostService {

    private final AILogMapper aiLogMapper;

    // 各AI供应商成本（元/1000 tokens）
    private static final Map<String, Double> COST_PER_1K_TOKENS = Map.of(
            "deepseek", 0.001,
            "openai", 0.01,
            "claude", 0.008,
            "kimi", 0.002,
            "glm", 0.001
    );

    /**
     * 记录AI调用日志
     */
    public void logAIRequest(Long userId, String provider, String model,
                             int inputTokens, int outputTokens,
                             String requestHash, boolean cacheHit,
                             int responseTimeMs, String status, String errorMessage) {
        try {
            AILog aiLog = new AILog();
            aiLog.setUserId(userId);
            aiLog.setProvider(provider);
            aiLog.setModel(model);
            aiLog.setInputTokens(inputTokens);
            aiLog.setOutputTokens(outputTokens);
            aiLog.setRequestHash(requestHash);
            aiLog.setCacheHit(cacheHit);
            aiLog.setResponseTimeMs(responseTimeMs);
            aiLog.setStatus(status);
            aiLog.setErrorMessage(errorMessage);

            // 计算成本
            if (!cacheHit && "success".equals(status)) {
                double cost = calculateCost(provider, inputTokens, outputTokens);
                aiLog.setCostAmount(BigDecimal.valueOf(cost));
            } else {
                aiLog.setCostAmount(BigDecimal.ZERO);
            }

            aiLogMapper.insert(aiLog);
        } catch (Exception e) {
            log.error("Failed to log AI request", e);
        }
    }

    /**
     * 计算成本
     */
    public double calculateCost(String provider, int inputTokens, int outputTokens) {
        double costPer1K = COST_PER_1K_TOKENS.getOrDefault(provider.toLowerCase(), 0.001);
        int totalTokens = inputTokens + outputTokens;
        return (totalTokens / 1000.0) * costPer1K;
    }

    /**
     * 每日成本报告与告警
     */
    @Scheduled(cron = "0 0 9 * * ?") // 每天9点执行
    public void dailyCostReport() {
        try {
            String date = LocalDate.now().minusDays(1).toString();
            List<AILog> logs = aiLogMapper.selectByDate(date);

            double totalCost = 0;
            int totalRequests = 0;
            int cacheHits = 0;

            for (AILog log : logs) {
                if (log.getCostAmount() != null) {
                    totalCost += log.getCostAmount().doubleValue();
                }
                totalRequests++;
                if (Boolean.TRUE.equals(log.getCacheHit())) {
                    cacheHits++;
                }
            }

            double cacheHitRate = totalRequests > 0 ? (double) cacheHits / totalRequests * 100 : 0;

            log.info("【AI成本日报】日期: {}, 总成本: ¥{:.2f}, 请求数: {}, 缓存命中率: {:.1f}%",
                    date, totalCost, totalRequests, cacheHitRate);

            // 每日成本超过100元触发告警
            if (totalCost > 100) {
                sendAlert("AI成本告警", String.format("昨日AI成本: ¥%.2f，超过阈值", totalCost));
            }

            // 缓存命中率低于50%告警
            if (cacheHitRate < 50 && totalRequests > 100) {
                sendAlert("缓存命中率告警", String.format("缓存命中率: %.1f%%，低于50%%", cacheHitRate));
            }

        } catch (Exception e) {
            log.error("Failed to generate daily cost report", e);
        }
    }

    /**
     * 发送告警（可接入钉钉/飞书/邮件等）
     */
    private void sendAlert(String title, String message) {
        log.warn("【告警】{} - {}", title, message);
        // TODO: 接入钉钉/飞书机器人
    }
}
