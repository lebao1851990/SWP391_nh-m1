package com.codeassess.submission.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Validates and exposes the shared internal API secret required for service-to-service
 * authentication between the BullMQ grading worker and Spring Boot backend.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.internal")
public class InternalAuthProperties {

    public static final String LEGACY_DEFAULT_SECRET = "codeassess-internal-secret-2026";

    /**
     * Shared secret for internal service-to-service calls (BullMQ worker -> Spring Boot).
     * Must be provided via the INTERNAL_API_SECRET environment variable.
     */
    private String apiSecret;

    @PostConstruct
    public void validateOnStartup() {
        validateSecret(this.apiSecret);
    }

    /**
     * Strictly validates the internal API secret against security rules:
     * - Must not be null
     * - Must not be empty or whitespace only
     * - Must not equal the insecure legacy default value
     *
     * @param secret Secret string to validate
     * @throws IllegalStateException if validation fails
     */
    public static void validateSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL SECURITY CONFIGURATION ERROR: INTERNAL_API_SECRET is missing or empty. " +
                "A non-blank secret is required for internal service-to-service authentication."
            );
        }
        if (LEGACY_DEFAULT_SECRET.equalsIgnoreCase(secret.trim())) {
            throw new IllegalStateException(
                "CRITICAL SECURITY CONFIGURATION ERROR: INTERNAL_API_SECRET cannot use the insecure legacy default value. " +
                "Configure a secure, random internal secret."
            );
        }
    }
}
