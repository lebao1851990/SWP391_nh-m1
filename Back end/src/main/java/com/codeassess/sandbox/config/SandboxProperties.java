package com.codeassess.sandbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sandbox resource limits and configuration, bound from {@code sandbox.*} in application.yml.
 * All byte-sized fields use long to avoid sign issues above 2 GiB.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    /** Docker image to run student code in. Must contain javac + java. */
    private String image = "eclipse-temurin:21-jdk-alpine";

    /** Hard wall-clock timeout for the entire compile + execute cycle (seconds). */
    private int timeoutSeconds = 10;

    /** Maximum number of simultaneous container executions. */
    private int maxConcurrentExecutions = 4;

    /** Container memory limit in bytes (hard cgroup limit). */
    private long memoryBytes = 512L * 1024 * 1024;   // 512 MiB

    /** CPU CFS period in microseconds. */
    private long cpuPeriodMicros = 100_000L;

    /** CPU CFS quota in microseconds (quota/period = fraction of one CPU). */
    private long cpuQuotaMicros = 50_000L;            // 0.5 CPU

    /** Maximum number of processes/threads inside the container. */
    private long pidsLimit = 50L;

    /** Maximum UTF-8-encoded byte length of accepted source code. */
    private int maxSourceBytes = 65_536;

    /** Maximum bytes captured from stdout or stderr (each). */
    private int maxOutputBytes = 65_536;

    /** Size of the tmpfs mount at /tmp inside the container (bytes). */
    private long tmpfsSizeBytes = 32L * 1024 * 1024;  // 32 MiB
}
