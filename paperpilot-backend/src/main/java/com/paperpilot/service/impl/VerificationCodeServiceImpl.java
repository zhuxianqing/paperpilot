package com.paperpilot.service.impl;

import com.paperpilot.service.EmailService;
import com.paperpilot.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

    @Value("${verification.code.length:6}")
    private int codeLength;

    @Value("${verification.code.expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${verification.code.resend-interval-seconds:60}")
    private int resendIntervalSeconds;

    private static final String CODE_KEY = "verify:code:%s";
    private static final String RESEND_KEY = "verify:resend:%s";
    private static final String RATE_LIMIT_KEY = "verify:ip:%s";

    private static final SecureRandom random = new SecureRandom();

    @Override
    public boolean sendCode(String email) {
        // 检查重发间隔
        String resendKey = String.format(RESEND_KEY, email);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(resendKey))) {
            log.warn("Code resend too frequently: {}", email);
            return false;
        }

        // 生成验证码
        String code = generateCode();

        // 保存到Redis
        String codeKey = String.format(CODE_KEY, email);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(expiryMinutes));

        // 设置重发限制
        redisTemplate.opsForValue().set(resendKey, "1", Duration.ofSeconds(resendIntervalSeconds));

        // 发送邮件
        emailService.sendVerificationCode(email, code);

        log.info("Verification code sent to: {}, code: {}", email, code);
        return true;
    }

    @Override
    public boolean verifyCode(String email, String code) {
        String codeKey = String.format(CODE_KEY, email);
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            log.warn("Code not found or expired: {}", email);
            return false;
        }

        boolean matched = storedCode.equalsIgnoreCase(code);
        if (matched) {
            log.info("Code verified for: {}", email);
        } else {
            log.warn("Code mismatch for: {}", email);
        }

        return matched;
    }

    @Override
    public void deleteCode(String email) {
        String codeKey = String.format(CODE_KEY, email);
        redisTemplate.delete(codeKey);
        log.info("Code deleted for: {}", email);
    }

    /**
     * 生成数字验证码
     */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
