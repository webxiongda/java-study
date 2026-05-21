package com.javastudy.security;

import com.javastudy.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationHours;

    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-expiration-hours}") long expirationHours) {
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
}
