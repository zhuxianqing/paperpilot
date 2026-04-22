package com.paperpilot.service;

public interface EmailService {

    /**
     * 发送验证码邮件
     */
    void sendVerificationCode(String toEmail, String code);
}
