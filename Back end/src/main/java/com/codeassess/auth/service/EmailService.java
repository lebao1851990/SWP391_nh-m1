package com.codeassess.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:lebao1851990@gmail.com}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.allow-mock-delivery:false}")
    private boolean allowMockDelivery;

    public EmailService() {
        this.mailSender = null;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public EmailService(JavaMailSender mailSender, String mailFrom, String mailUsername, boolean allowMockDelivery) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.allowMockDelivery = allowMockDelivery;
    }

    /**
     * Dispatches password reset OTP verification email.
     * Note: Plaintext OTP is NEVER written to logs.
     */
    public void sendPasswordResetOtp(String toEmail, String otpCode) {
        log.info("Initiating password reset email delivery to: {}", maskEmail(toEmail));

        if (mailSender == null) {
            if (allowMockDelivery) {
                log.warn("JavaMailSender bean is not available. Mock email delivery simulated for {}", maskEmail(toEmail));
                return;
            } else {
                log.error("JavaMailSender bean is not configured in production mode.");
                throw new IllegalStateException("Email delivery service is misconfigured. Please contact system administrator.");
            }
        }

        if (mailUsername == null || mailUsername.isBlank()) {
            if (allowMockDelivery) {
                log.warn("MAIL_USERNAME is not configured in environment. Mock email delivery simulated for {}", maskEmail(toEmail));
                return;
            } else {
                log.error("MAIL_USERNAME is not configured in production mode. Refusing silent mock delivery.");
                throw new IllegalStateException("Email delivery credentials are not configured. Please contact system administrator.");
            }
        }

        try {
            String fromAddress = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : mailUsername;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("CodeAssess - Password Reset Verification Code");
            message.setText("Hello,\n\n"
                    + "We received a request to reset your password for your CodeAssess account.\n\n"
                    + "Your 6-digit verification code is: " + otpCode + "\n\n"
                    + "This code is valid for 10 minutes. If you did not request a password reset, please ignore this email.\n\n"
                    + "Best regards,\n"
                    + "CodeAssess Security Team");

            mailSender.send(message);
            log.info("Password reset verification email successfully sent to {}", maskEmail(toEmail));
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP authentication failed for recipient {}: Verify MAIL_USERNAME and MAIL_PASSWORD (Gmail App Password). Error: {}",
                    maskEmail(toEmail), e.getMessage());
            throw new RuntimeException("Email authentication failed. Please contact system administrator.", e);
        } catch (org.springframework.mail.MailSendException e) {
            log.error("SMTP transport error for recipient {}: Check MAIL_HOST, MAIL_PORT, and network connectivity. Error: {}",
                    maskEmail(toEmail), e.getMessage());
            throw new RuntimeException("Unable to send verification email. Please try again later.", e);
        } catch (Exception e) {
            log.error("Failed to deliver password reset email to {}: {}", maskEmail(toEmail), e.getMessage());
            // Do not leak SMTP credentials or raw internal network details in exception message
            throw new RuntimeException("Unable to send verification email. Please try again later.", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
