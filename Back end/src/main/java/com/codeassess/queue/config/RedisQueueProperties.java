package com.codeassess.queue.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for CodeAssess Redis queue infrastructure.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "queue.plagiarism")
public class RedisQueueProperties {

    /** Whether Redis queue publishing is enabled. */
    private boolean enabled = true;

    /** Redis Stream key name for plagiarism jobs. */
    private String streamName = "codeassess:jobs:plagiarism";

    /** Consumer group name for distributed worker pools. */
    private String consumerGroup = "plagiarism-worker-group";

    /** Maximum retries allowed before routing to dead-letter stream. */
    private int maxRetries = 3;

    /** Publish timeout in milliseconds before timing out publication. */
    private long publishTimeoutMs = 2000L;
}
