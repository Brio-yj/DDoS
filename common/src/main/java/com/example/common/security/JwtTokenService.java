package com.example.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtProperties properties;
    private final Key signingKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        if (properties.getSecret() == null || properties.getSecret().length() < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenValiditySeconds());
        return buildToken(userId, now, expiry, Map.of(
                "token_type", "ACCESS",
                "email", email,
                "roles", roles
        ));
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getRefreshTokenValiditySeconds());
        return buildToken(userId, now, expiry, Map.of("token_type", "REFRESH"));
    }

    private String buildToken(Long userId, Instant issuedAt, Instant expiresAt, Map<String, Object> claims) {
        JwtBuilder builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuer(properties.getIssuer())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey, SignatureAlgorithm.HS256);
        claims.forEach(builder::claim);
        return builder.compact();
    }

    public JwtPrincipal extractPrincipal(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get("token_type", String.class);
        if (!"ACCESS".equals(tokenType)) {
            throw new JwtException("Token is not an access token");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        return new JwtPrincipal(userId, email, roles);
    }

    public boolean isAccessTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return "ACCESS".equals(claims.get("token_type", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid access token: {}", ex.getMessage());
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return "REFRESH".equals(claims.get("token_type", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid refresh token: {}", ex.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
