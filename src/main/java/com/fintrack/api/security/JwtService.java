package com.fintrack.api.security;

import com.fintrack.api.domain.entity.SourceCredential;
import com.fintrack.common.domain.SourceType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expiryHours;

    public JwtService(@Value("${fintrack.jwt.secret}") String secret,
                      @Value("${fintrack.jwt.expiry-hours}") long expiryHours) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryHours = expiryHours;
    }

    public String generateToken(SourceCredential credential) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(credential.getUsername())
                .claim("source_id", credential.getSourceId().toString())
                .claim("source_type", credential.getSourceType().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryHours, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    public SourceIdentity validateAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID sourceId = UUID.fromString(claims.get("source_id", String.class));
        SourceType sourceType = SourceType.valueOf(claims.get("source_type", String.class));

        return new SourceIdentity(sourceId, sourceType);
    }
}
