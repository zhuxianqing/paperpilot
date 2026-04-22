package com.paperpilot.service;

public interface VerificationCodeService {

    /**
     * 生成并发送验证码
     * @return true-发送成功 false-发送过于频繁
     */
    boolean sendCode(String email);

    /**
     * 校验验证码
     * @return true-校验成功
     */
    boolean verifyCode(String email, String code);

    /**
     * 删除已使用的验证码
     */
    void deleteCode(String email);
}
