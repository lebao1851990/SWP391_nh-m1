package com.codeassess.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class PasswordResetTokenStore {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.auth.password-reset.allow-in-memory-fallback:false}")
    private boolean allowInMemoryFallback;

    // In-memory store used in development/test or if explicitly allowed via configuration
    private final Map<String, InMemoryOtpEntry> inMemoryOtpStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> inMemoryRateLimitStore = new ConcurrentHashMap<>();
    private final Map<String, InMemoryTokenEntry> inMemoryTokenStore = new ConcurrentHashMap<>();
    private final Map<String, InMemoryClaimEntry> inMemoryClaimStore = new ConcurrentHashMap<>();
    private final Map<String, Long> inMemoryPasswordChangedStore = new ConcurrentHashMap<>();

    private record InMemoryClaimEntry(String owner, Instant expiresAt) {}

    // Redis Lua Script: Atomic OTP verification, attempt tracking, and single-use consumption
    private static final RedisScript<Long> VERIFY_OTP_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local expectedHash = ARGV[1]
            local maxAttempts = tonumber(ARGV[2])

            local val = redis.call('GET', key)
            if not val then
                return -1
            end

            local sep1 = string.find(val, ":")
            if not sep1 then return -1 end
            local sep2 = string.find(val, ":", sep1 + 1)
            if not sep2 then return -1 end

            local hashedOtp = string.sub(val, 1, sep1 - 1)
            local attempts = tonumber(string.sub(val, sep1 + 1, sep2 - 1))
            local expMillis = tonumber(string.sub(val, sep2 + 1))

            if attempts >= maxAttempts then
                redis.call('DEL', key)
                return -2
            end

            attempts = attempts + 1

            if hashedOtp == expectedHash then
                redis.call('DEL', key)
                return 1
            else
                if attempts >= maxAttempts then
                    redis.call('DEL', key)
                    return -2
                else
                    local ttl = redis.call('PTTL', key)
                    if ttl > 0 then
                        local newVal = hashedOtp .. ":" .. attempts .. ":" .. expMillis
                        redis.call('SET', key, newVal, 'PX', ttl)
                    end
                    return 0
                end
            end
            """,
            Long.class
    );

    // Redis Lua Script: Atomic rollback of OTP and rate-limit on SMTP transport failure
    private static final RedisScript<Long> ROLLBACK_OTP_SCRIPT = new DefaultRedisScript<>(
            """
            local otpKey = KEYS[1]
            local rateKey = KEYS[2]
            local expectedHash = ARGV[1]

            local val = redis.call('GET', otpKey)
            if val then
                local sep = string.find(val, ":")
                local currentHash = sep and string.sub(val, 1, sep - 1) or val
                if currentHash == expectedHash then
                    redis.call('DEL', otpKey)
                    redis.call('DEL', rateKey)
                    return 1
                end
            end
            return 0
            """,
            Long.class
    );

    // Redis Lua Script: Claim release with ownership verification (fencing)
    private static final RedisScript<Long> RELEASE_CLAIM_SCRIPT = new DefaultRedisScript<>(
            """
            local claimKey = KEYS[1]
            local claimOwner = ARGV[1]
            local currentOwner = redis.call('GET', claimKey)
            if currentOwner == claimOwner then
                redis.call('DEL', claimKey)
                return 1
            end
            return 0
            """,
            Long.class
    );

    // Redis Lua Script: Token consume with claim ownership verification (fencing)
    private static final RedisScript<Long> CONSUME_TOKEN_SCRIPT = new DefaultRedisScript<>(
            """
            local tokenKey = KEYS[1]
            local claimKey = KEYS[2]
            local claimOwner = ARGV[1]
            local currentOwner = redis.call('GET', claimKey)
            if currentOwner == claimOwner then
                redis.call('DEL', tokenKey)
                redis.call('DEL', claimKey)
                return 1
            end
            return 0
            """,
            Long.class
    );

    public PasswordResetTokenStore() {
        this.redisTemplate = null;
        this.allowInMemoryFallback = true;
    }

    @Autowired(required = false)
    public PasswordResetTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.allowInMemoryFallback = (redisTemplate == null);
    }

    public PasswordResetTokenStore(StringRedisTemplate redisTemplate, boolean allowInMemoryFallback) {
        this.redisTemplate = redisTemplate;
        this.allowInMemoryFallback = allowInMemoryFallback;
    }

    public enum VerifyStatus {
        SUCCESS,
        NOT_FOUND_OR_EXPIRED,
        MAX_ATTEMPTS_EXCEEDED,
        MISMATCH
    }

    public record VerifyResult(VerifyStatus status, int currentAttempts) {}
    public record OtpEntry(String hashedOtp, int attempts, Instant expiresAt) {}
    private record InMemoryOtpEntry(String hashedOtp, int attempts, Instant expiresAt) {}
    private record InMemoryTokenEntry(String email, Instant expiresAt) {}

    // --- Key Generators with Redis Cluster Hash Tags ({...}) ---

    private String otpKey(String email) {
        return "pwd_reset:{" + email + "}:otp";
    }

    private String rateKey(String email) {
        return "pwd_reset:{" + email + "}:rate";
    }

    private String pwdChangedKey(String email) {
        return "pwd_reset:{" + email + "}:pwd_changed";
    }

    private String tokenKey(String hashedToken) {
        return "pwd_reset:{" + hashedToken + "}:token";
    }

    private String claimKey(String hashedToken) {
        return "pwd_reset:{" + hashedToken + "}:claim";
    }

    // --- OTP Operations ---

    public void saveOtp(String email, String hashedOtp, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        if (redisTemplate != null) {
            try {
                String key = otpKey(email);
                String value = hashedOtp + ":" + 0 + ":" + expiresAt.toEpochMilli();
                redisTemplate.opsForValue().set(key, value, ttl);
                return;
            } catch (Exception e) {
                handleRedisFailure("saveOtp", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryOtpStore.put(email, new InMemoryOtpEntry(hashedOtp, 0, expiresAt));
        }
    }

    /**
     * Atomically verifies the OTP, increments attempt count, and consumes it on match.
     * Prevents race conditions and attempt limit bypassing across concurrent threads/instances.
     */
    public VerifyResult verifyAndConsumeOtp(String email, String expectedHashedOtp, int maxAttempts) {
        if (redisTemplate != null) {
            try {
                String key = otpKey(email);
                Long scriptResult = redisTemplate.execute(
                        VERIFY_OTP_SCRIPT,
                        Collections.singletonList(key),
                        expectedHashedOtp,
                        String.valueOf(maxAttempts)
                );

                if (scriptResult != null) {
                    long code = scriptResult;
                    if (code == 1L) {
                        return new VerifyResult(VerifyStatus.SUCCESS, 1);
                    } else if (code == 0L) {
                        return new VerifyResult(VerifyStatus.MISMATCH, 1);
                    } else if (code == -2L) {
                        return new VerifyResult(VerifyStatus.MAX_ATTEMPTS_EXCEEDED, maxAttempts);
                    } else if (code == -1L) {
                        // If not found in Redis, check if it was stored in memory fallback (dev/test split-brain prevention)
                        if (allowInMemoryFallback && inMemoryOtpStore.containsKey(email)) {
                            return verifyAndConsumeOtpInMemory(email, expectedHashedOtp, maxAttempts);
                        }
                        return new VerifyResult(VerifyStatus.NOT_FOUND_OR_EXPIRED, 0);
                    }
                }
            } catch (Exception e) {
                handleRedisFailure("verifyAndConsumeOtp", e);
            }
        }

        if (allowInMemoryFallback) {
            return verifyAndConsumeOtpInMemory(email, expectedHashedOtp, maxAttempts);
        }

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication store service is unavailable.");
    }

    private VerifyResult verifyAndConsumeOtpInMemory(String email, String expectedHashedOtp, int maxAttempts) {
        AtomicReference<VerifyResult> resultRef = new AtomicReference<>();
        inMemoryOtpStore.compute(email, (k, entry) -> {
            if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
                resultRef.set(new VerifyResult(VerifyStatus.NOT_FOUND_OR_EXPIRED, 0));
                return null;
            }
            if (entry.attempts() >= maxAttempts) {
                resultRef.set(new VerifyResult(VerifyStatus.MAX_ATTEMPTS_EXCEEDED, entry.attempts()));
                return null;
            }

            int newAttempts = entry.attempts() + 1;
            boolean isMatch = MessageDigest.isEqual(
                    expectedHashedOtp.getBytes(StandardCharsets.UTF_8),
                    entry.hashedOtp().getBytes(StandardCharsets.UTF_8)
            );

            if (isMatch) {
                resultRef.set(new VerifyResult(VerifyStatus.SUCCESS, newAttempts));
                return null; // Consumed atomically
            } else {
                if (newAttempts >= maxAttempts) {
                    resultRef.set(new VerifyResult(VerifyStatus.MAX_ATTEMPTS_EXCEEDED, newAttempts));
                    return null; // Locked out
                } else {
                    resultRef.set(new VerifyResult(VerifyStatus.MISMATCH, newAttempts));
                    return new InMemoryOtpEntry(entry.hashedOtp(), newAttempts, entry.expiresAt());
                }
            }
        });

        VerifyResult result = resultRef.get();
        return result != null ? result : new VerifyResult(VerifyStatus.NOT_FOUND_OR_EXPIRED, 0);
    }

    public OtpEntry getOtp(String email) {
        if (redisTemplate != null) {
            try {
                String key = otpKey(email);
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    String[] parts = val.split(":");
                    if (parts.length >= 3) {
                        String hashedOtp = parts[0];
                        int attempts = Integer.parseInt(parts[1]);
                        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
                        if (Instant.now().isBefore(expiresAt)) {
                            return new OtpEntry(hashedOtp, attempts, expiresAt);
                        } else {
                            deleteOtp(email);
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                handleRedisFailure("getOtp", e);
            }
        }
        if (allowInMemoryFallback) {
            InMemoryOtpEntry entry = inMemoryOtpStore.get(email);
            if (entry != null) {
                if (Instant.now().isBefore(entry.expiresAt())) {
                    return new OtpEntry(entry.hashedOtp(), entry.attempts(), entry.expiresAt());
                }
                inMemoryOtpStore.remove(email);
            }
        }
        return null;
    }

    public void incrementOtpAttempts(String email) {
        if (redisTemplate != null) {
            try {
                String key = otpKey(email);
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    String[] parts = val.split(":");
                    if (parts.length >= 3) {
                        String hashedOtp = parts[0];
                        int attempts = Integer.parseInt(parts[1]) + 1;
                        long expMillis = Long.parseLong(parts[2]);
                        long remainingTtl = expMillis - System.currentTimeMillis();
                        if (remainingTtl > 0) {
                            redisTemplate.opsForValue().set(key, hashedOtp + ":" + attempts + ":" + expMillis, Duration.ofMillis(remainingTtl));
                        }
                    }
                }
                return;
            } catch (Exception e) {
                handleRedisFailure("incrementOtpAttempts", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryOtpStore.computeIfPresent(email, (k, v) -> new InMemoryOtpEntry(v.hashedOtp(), v.attempts() + 1, v.expiresAt()));
        }
    }

    public void deleteOtp(String email) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(otpKey(email));
                return;
            } catch (Exception e) {
                handleRedisFailure("deleteOtp", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryOtpStore.remove(email);
        }
    }

    /**
     * Atomically rolls back both the OTP and the rate limit when email delivery fails.
     * Uses Cluster-compatible hashtag {email} so keys hash to the same node slot.
     */
    public void rollbackOtpAndRateLimit(String email, String expectedHashedOtp) {
        if (redisTemplate != null) {
            try {
                String oKey = otpKey(email);
                String rKey = rateKey(email);
                redisTemplate.execute(
                        ROLLBACK_OTP_SCRIPT,
                        List.of(oKey, rKey),
                        expectedHashedOtp
                );
                return;
            } catch (Exception e) {
                handleRedisFailure("rollbackOtpAndRateLimit", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryOtpStore.computeIfPresent(email, (k, entry) -> {
                if (entry.hashedOtp().equals(expectedHashedOtp)) {
                    inMemoryRateLimitStore.remove(email);
                    return null;
                }
                return entry;
            });
        }
    }

    // --- Rate Limit Operations ---

    public boolean isRateLimited(String email) {
        if (redisTemplate != null) {
            try {
                String key = rateKey(email);
                Boolean hasKey = redisTemplate.hasKey(key);
                if (Boolean.TRUE.equals(hasKey)) {
                    return true;
                }
            } catch (Exception e) {
                handleRedisFailure("isRateLimited", e);
            }
        }
        if (allowInMemoryFallback) {
            Instant lastSent = inMemoryRateLimitStore.get(email);
            return lastSent != null && Instant.now().isBefore(lastSent);
        }
        return false;
    }

    public void setRateLimit(String email, Duration ttl) {
        if (redisTemplate != null) {
            try {
                String key = rateKey(email);
                redisTemplate.opsForValue().set(key, "1", ttl);
                return;
            } catch (Exception e) {
                handleRedisFailure("setRateLimit", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryRateLimitStore.put(email, Instant.now().plus(ttl));
        }
    }

    public void clearRateLimit(String email) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(rateKey(email));
                return;
            } catch (Exception e) {
                handleRedisFailure("clearRateLimit", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryRateLimitStore.remove(email);
        }
    }

    // --- Reset Token Operations with Claim Ownership (Fencing) ---

    public void saveResetToken(String hashedToken, String email, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        if (redisTemplate != null) {
            try {
                String key = tokenKey(hashedToken);
                redisTemplate.opsForValue().set(key, email, ttl);
                return;
            } catch (Exception e) {
                handleRedisFailure("saveResetToken", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryTokenStore.put(hashedToken, new InMemoryTokenEntry(email, expiresAt));
        }
    }

    public boolean claimResetToken(String hashedToken, Duration claimTtl) {
        return claimResetToken(hashedToken, "1", claimTtl);
    }

    /**
     * Attempts to acquire an atomic claim lock on the reset token with a unique owner token.
     * Guarantees that only one concurrent request executes the reset operation.
     */
    public boolean claimResetToken(String hashedToken, String claimOwner, Duration claimTtl) {
        String cKey = claimKey(hashedToken);
        if (redisTemplate != null) {
            try {
                Boolean claimed = redisTemplate.opsForValue().setIfAbsent(cKey, claimOwner, claimTtl);
                return Boolean.TRUE.equals(claimed);
            } catch (Exception e) {
                handleRedisFailure("claimResetToken", e);
            }
        }
        if (allowInMemoryFallback) {
            Instant expiresAt = Instant.now().plus(claimTtl);
            InMemoryClaimEntry entry = inMemoryClaimStore.compute(hashedToken, (k, existing) -> {
                if (existing == null || Instant.now().isAfter(existing.expiresAt())) {
                    return new InMemoryClaimEntry(claimOwner, expiresAt);
                }
                return existing;
            });
            return entry != null && entry.owner().equals(claimOwner);
        }
        return false;
    }

    public void releaseResetTokenClaim(String hashedToken) {
        releaseResetTokenClaim(hashedToken, null);
    }

    /**
     * Releases claim lock ONLY if the claim is still owned by the specified claimOwner.
     * Prevents an expired request from releasing a lock acquired by a newer request.
     */
    public boolean releaseResetTokenClaim(String hashedToken, String claimOwner) {
        String cKey = claimKey(hashedToken);
        if (redisTemplate != null) {
            try {
                if (claimOwner == null) {
                    redisTemplate.delete(cKey);
                    return true;
                }
                Long res = redisTemplate.execute(RELEASE_CLAIM_SCRIPT, Collections.singletonList(cKey), claimOwner);
                return res != null && res == 1L;
            } catch (Exception e) {
                handleRedisFailure("releaseResetTokenClaim", e);
            }
        }
        if (allowInMemoryFallback) {
            AtomicBoolean released = new AtomicBoolean(false);
            inMemoryClaimStore.computeIfPresent(hashedToken, (k, existing) -> {
                if (Instant.now().isAfter(existing.expiresAt())) {
                    return null; // Claim expired and evicted
                }
                if (claimOwner == null || existing.owner().equals(claimOwner)) {
                    released.set(true);
                    return null;
                }
                return existing;
            });
            return released.get();
        }
        return false;
    }

    public void consumeResetToken(String hashedToken) {
        consumeResetToken(hashedToken, null);
    }

    /**
     * Consumes both token and claim atomically ONLY if the claim is still owned by claimOwner.
     * Uses Cluster-compatible hashtag {hashedToken} for Redis Cluster safety.
     */
    public boolean consumeResetToken(String hashedToken, String claimOwner) {
        String tKey = tokenKey(hashedToken);
        String cKey = claimKey(hashedToken);
        if (redisTemplate != null) {
            try {
                if (claimOwner == null) {
                    redisTemplate.delete(List.of(tKey, cKey));
                    return true;
                }
                Long res = redisTemplate.execute(CONSUME_TOKEN_SCRIPT, List.of(tKey, cKey), claimOwner);
                return res != null && res == 1L;
            } catch (Exception e) {
                handleRedisFailure("consumeResetToken", e);
            }
        }
        if (allowInMemoryFallback) {
            AtomicBoolean consumed = new AtomicBoolean(false);
            inMemoryClaimStore.computeIfPresent(hashedToken, (k, existing) -> {
                if (Instant.now().isAfter(existing.expiresAt())) {
                    return null; // Claim expired and evicted
                }
                if (claimOwner == null || existing.owner().equals(claimOwner)) {
                    inMemoryTokenStore.remove(hashedToken);
                    consumed.set(true);
                    return null;
                }
                return existing;
            });
            return consumed.get();
        }
        return false;
    }

    public String getResetTokenEmail(String hashedToken) {
        if (redisTemplate != null) {
            try {
                String key = tokenKey(hashedToken);
                String email = redisTemplate.opsForValue().get(key);
                if (email != null) {
                    return email;
                }
            } catch (Exception e) {
                handleRedisFailure("getResetTokenEmail", e);
            }
        }
        if (allowInMemoryFallback) {
            InMemoryTokenEntry entry = inMemoryTokenStore.get(hashedToken);
            if (entry != null) {
                if (Instant.now().isBefore(entry.expiresAt())) {
                    return entry.email();
                }
                inMemoryTokenStore.remove(hashedToken);
            }
        }
        return null;
    }

    public void deleteResetToken(String hashedToken) {
        consumeResetToken(hashedToken, null);
    }

    // --- Session Invalidation Timestamp Operations ---

    public void recordPasswordChanged(String email, Instant timestamp) {
        Duration ttl = Duration.ofDays(30);
        long epochMillis = timestamp.toEpochMilli();
        if (redisTemplate != null) {
            try {
                String key = pwdChangedKey(email);
                redisTemplate.opsForValue().set(key, String.valueOf(epochMillis), ttl);
                return;
            } catch (Exception e) {
                handleRedisFailure("recordPasswordChanged", e);
            }
        }
        if (allowInMemoryFallback) {
            inMemoryPasswordChangedStore.put(email, epochMillis);
        }
    }

    public Long getPasswordChangedAt(String email) {
        if (redisTemplate != null) {
            try {
                String key = pwdChangedKey(email);
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    return Long.parseLong(val);
                }
            } catch (Exception e) {
                handleRedisFailure("getPasswordChangedAt", e);
            }
        }
        if (allowInMemoryFallback) {
            return inMemoryPasswordChangedStore.get(email);
        }
        return null;
    }

    public void clearPasswordChangedAt(String email) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(pwdChangedKey(email));
            } catch (Exception e) {
                handleRedisFailure("clearPasswordChangedAt", e);
            }
        }
        inMemoryPasswordChangedStore.remove(email);
    }

    // --- Health & Connection Check ---

    public boolean isRedisAvailable() {
        if (redisTemplate == null) {
            return false;
        }
        try {
            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory != null) {
                var conn = connectionFactory.getConnection();
                try {
                    return "PONG".equalsIgnoreCase(conn.ping());
                } finally {
                    conn.close();
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("Redis liveness check failed: {}", e.getMessage());
            return false;
        }
    }

    private void handleRedisFailure(String operation, Exception e) {
        log.error("Redis operation '{}' failed: {}", operation, e.getMessage());
        if (!allowInMemoryFallback) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication store service is temporarily unavailable. Please try again shortly."
            );
        }
    }
}
