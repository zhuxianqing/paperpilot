package com.paperpilot.service.impl;

import com.paperpilot.service.EmailService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Value("${mail.smtp.host:smtp.qq.com}")
    private String smtpHost;

    @Value("${mail.smtp.port:587}")
    private String smtpPort;

    @Value("${mail.sender.username:}")
    private String senderUsername;

    @Value("${mail.sender.auth-code:}")
    private String senderAuthCode;

    private Session mailSession;

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        Session session = getMailSession();
        if (session == null) {
            log.error("邮件服务配置不完整，无法发送邮件");
            throw new RuntimeException("邮件服务配置不完整");
        }

        try {
            long startTime = System.currentTimeMillis();
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderUsername));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("PaperPilot - 邮箱验证码");

            String content = buildEmailContent(code);
            message.setContent(content, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("验证码邮件发送成功，接收邮箱: {}，耗时: {}ms", toEmail, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("验证码邮件发送失败，接收邮箱: {}", toEmail, e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    private Session getMailSession() {
        if (mailSession != null) {
            return mailSession;
        }

        if (smtpHost == null || smtpHost.isEmpty() ||
            smtpPort == null || smtpPort.isEmpty() ||
            senderUsername == null || senderUsername.isEmpty()) {
            log.error("邮件配置信息不完整: host={}, port={}, username={}", smtpHost, smtpPort, senderUsername);
            return null;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderUsername, senderAuthCode);
            }
        });

        log.info("邮件会话初始化成功: host={}, port={}, username={}", smtpHost, smtpPort, senderUsername);
        return mailSession;
    }

    private String buildEmailContent(String code) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    .container { max-width: 600px; margin: 0 auto; font-family: Arial, sans-serif; }
                    .header { background: #4F46E5; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; background: #f9fafb; }
                    .code { font-size: 32px; font-weight: bold; color: #4F46E5;
                            letter-spacing: 8px; text-align: center; padding: 20px; }
                    .footer { padding: 20px; text-align: center; color: #6b7280; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>PaperPilot 学术文献助手</h1>
                    </div>
                    <div class="content">
                        <p>您好，</p>
                        <p>您的邮箱验证码为：</p>
                        <div class="code">%s</div>
                        <p>验证码有效期为 10 分钟，请勿泄露给他人。</p>
                        <p>如非本人操作，请忽略此邮件。</p>
                    </div>
                    <div class="footer">
                        <p>此邮件由系统自动发送，请勿回复</p>
                        <p>© 2025 PaperPilot</p>
                    </div>
                </div>
            </body>
            </html>
            """, code);
    }
}
