package edu.university.plis.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "plis.security")
public record JwtProperties(String jwtSecret, Duration tokenLifetime) {
    public JwtProperties {
        if (tokenLifetime == null || tokenLifetime.isNegative() || tokenLifetime.isZero()) {
            tokenLifetime = Duration.ofHours(8);
        }
    }
}
