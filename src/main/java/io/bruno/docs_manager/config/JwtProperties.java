package io.bruno.docs_manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret   HMAC key for signing access tokens; must be at least 32 bytes for HS256
 * @param issuer   value placed in the {@code iss} claim
 * @param lifetime how long an issued token stays valid
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration lifetime) {

    public JwtProperties {
        issuer = issuer == null ? "docs-manager" : issuer;
        lifetime = lifetime == null ? Duration.ofHours(1) : lifetime;
    }
}
