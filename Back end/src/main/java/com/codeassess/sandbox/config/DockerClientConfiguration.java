package com.codeassess.sandbox.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Provides a Spring-managed {@link DockerClient} bean using the zero-dependency
 * HTTP transport to avoid JAX-RS classpath conflicts in Spring Boot 3.
 *
 * <p>When sandbox execution is delegated to the dedicated Sandbox Service,
 * direct DockerClient initialization is disabled by default.
 */
@Configuration
public class DockerClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "sandbox.docker.direct-client.enabled", havingValue = "true", matchIfMissing = false)
    public DockerClient dockerClient() {
        com.github.dockerjava.core.DockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
