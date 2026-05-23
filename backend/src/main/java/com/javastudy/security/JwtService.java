package com.javastudy.security;

import com.javastudy.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String DEFAULT_DEV_SECRET = "change-this-local-development-secret-at-least-32-chars";

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(
        @Value("${app.jwt-secret}") String secret,
        @Value("${app.jwt-expiration-hours}") long expirationHours,
        Environment environment
    ) {
        validateSecret(secret, environment);
        this.key = Keys.hmacShaKeyFor(normalize(secret).getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String createToken(User user) {
        var now = Instant.now();
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("uid", user.getId())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
            .signWith(key)
            .compact();
    }

    public String subject(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    private String normalize(String secret) {
        if (secret.length() >= 32) {
            return secret;
        }
        return (secret + "00000000000000000000000000000000").substring(0, 32);
    }

    private void validateSecret(String secret, Environment environment) {
        if (!requiresStrictSecret(environment)) {
            return;
        }
        if (secret == null || secret.length() < 32 || DEFAULT_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException("app.jwt-secret must be set to a non-default value of at least 32 characters outside local/test profiles");
        }
    }

    private boolean requiresStrictSecret(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
            .noneMatch(profile -> "local".equals(profile) || "test".equals(profile));
    }
}
