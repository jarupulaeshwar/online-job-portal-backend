package com.jobportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed access to the "app.*" settings in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Storage storage, Seed seed, Admin admin, Demo demo) {
    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(String uploadDir, long maxResumeBytes) {}

    public record Seed(boolean demoData) {}

    public record Admin(String email, String password, String name) {}

    public record Demo(String password) {}
}
