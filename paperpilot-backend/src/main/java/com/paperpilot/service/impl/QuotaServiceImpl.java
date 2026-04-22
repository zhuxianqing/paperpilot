package com.paperpilot.service.impl;

import com.paperpilot.dto.response.QuotaDeductResult;
import com.paperpilot.dto.response.QuotaVO;
import com.paperpilot.entity.QuotaTransaction;
import com.paperpilot.entity.User;
import com.paperpilot.mapper.QuotaTransactionMapper;
import com.paperpilot.mapper.UserMapper;
import com.paperpilot.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final QuotaTransactionMapper transactionMapper;

    @Value("${free-quota.daily-limit:3}")
    private Integer freeQuotaDailyLimit;

    @Value("${byok.daily-limit:50}")
    private Integer byokDailyLimit;

    private static final String FREE_QUOTA_KEY = "free_quota:%s:%s";
    private static final String BYOK_LIMIT_KEY = "byok_limit:%s:%s";

    @Override
    public QuotaVO getQuotaInfo(Long userId) {
        User user = userMapper.selectById(userId);

        String today = LocalDate.now().toString();
        String freeQuotaKey = String.format(FREE_QUOTA_KEY, userId, today);
        String freeUsedStr = redisTemplate.opsForValue().get(freeQuotaKey);
        int freeUsed = freeUsedStr == null ? 0 : Integer.parseInt(freeUsedStr);

        return QuotaVO.builder()
                .balance(user.getQuotaBalance())
                .freeQuotaUsed(freeUsed)
                .freeQuotaTotal(freeQuotaDailyLimit)
                .isVip(user.getIsVip())
                .vipExpireAt(user.getVipExpireAt())
                .build();
    }

    @Override
    @Transactional
    public QuotaDeductResult deductQuota(Long userId, int amount, String reason, Long taskId) {
        // 先尝试扣除付费额度
        int affected = userMapper.deductQuota(userId, amount);

        if (affected > 0) {
            // 记录交易
            recordTransaction(userId, -amount, "consume", null, taskId, reason);
            return QuotaDeductResult.builder()
                    .success(true)
                    .usedFreeQuota(false)
                    .amount(amount)
                    .build();
        }

        // 付费额度不足，检查免费额度
        String today = LocalDate.now().toString();
        String freeQuotaKey = String.format(FREE_QUOTA_KEY, userId, today);

        Long currentUsed = redisTemplate.opsForValue().increment(freeQuotaKey);
        redisTemplate.expire(freeQuotaKey, Duration.ofDays(1));

        if (currentUsed != null && currentUsed <= freeQuotaDailyLimit) {
            recordTransaction(userId, -amount, "consume", null, taskId,
                    reason + "(使用免费额度)");
            return QuotaDeductResult.builder()
                    .success(true)
                    .usedFreeQuota(true)
                    .amount(amount)
                    .build();
        }

        // 免费额度也已用完，回滚计数
        redisTemplate.opsForValue().decrement(freeQuotaKey);
        return QuotaDeductResult.builder()
                .success(false)
                .usedFreeQuota(false)
                .amount(0)
                .build();
    }

    @Override
    @Transactional
    public void addQuota(Long userId, int amount, String reason, Long orderId) {
        userMapper.addQuota(userId, amount);
        recordTransaction(userId, amount, "recharge", orderId, null, reason);
    }

    @Override
    @Transactional
    public void rollbackQuota(Long userId, int amount, boolean usedFreeQuota) {
        if (usedFreeQuota) {
            // 回滚免费额度，递减Redis计数器
            String today = LocalDate.now().toString();
            String freeQuotaKey = String.format(FREE_QUOTA_KEY, userId, today);
            redisTemplate.opsForValue().decrement(freeQuotaKey);
            // 记录退款交易
            recordTransaction(userId, amount, "refund", null, null,
                    "AI分析失败退款(免费额度)");
        } else {
            // 回滚付费额度
            userMapper.addQuota(userId, amount);
            recordTransaction(userId, amount, "refund", null, null,
                    "AI分析失败退款");
        }
    }

    @Override
    public boolean checkByokDailyLimit(Long userId) {
        String today = LocalDate.now().toString();
        String key = String.format(BYOK_LIMIT_KEY, userId, today);

        Long current = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(1));

        return current != null && current <= byokDailyLimit;
    }

    private void recordTransaction(Long userId, int amount, String type,
                                   Long orderId, Long taskId, String description) {
        QuotaTransaction transaction = new QuotaTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setOrderId(orderId);
        transaction.setTaskId(taskId);
        transaction.setDescription(description);
        transactionMapper.insert(transaction);
    }
}
