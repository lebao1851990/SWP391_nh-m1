package com.codeassess.sandbox.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Provides a Spring-managed {@link DockerClient} bean using the zero-dependency
 * HTTP transport to avoid JAX-RS classpath conflicts in Spring Boot 3.
 *
 * <p>The client connects to the local Docker daemon only. It does NOT mount the
 * Docker socket into any student container — the socket is used exclusively by
 * the host-side JVM to orchestrate container lifecycle.
 *
 * <p>Note: previously named {@code DockerClientConfig}, which conflicted with
 * the imported {@code com.github.dockerjava.core.DockerClientConfig} type,
 * causing a "already defined in this compilation unit" error that broke
 * the entire compilation (including Lombok annotation processing).
 */
@Configuration
public class DockerClientConfiguration {

    @Bean
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
