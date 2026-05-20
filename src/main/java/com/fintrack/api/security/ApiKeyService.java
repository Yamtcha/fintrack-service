package com.fintrack.api.security;

import com.fintrack.common.domain.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ApiKeyService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretBytes;

    public ApiKeyService(@Value("${api.key}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateKey(UUID sourceId, SourceType sourceType) {
        String payload = sourceId + ":" + sourceType.name();
        String signature = hmacHex(payload);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + signature;
    }

    public Optional<SourceIdentity> validate(String rawKey) {
        String[] parts = rawKey.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String expectedSig = hmacHex(payload);

            if (!constantTimeEquals(expectedSig, parts[1])) {
                return Optional.empty();
            }

            String[] payloadParts = payload.split(":", 2);
            UUID sourceId = UUID.fromString(payloadParts[0]);
            SourceType sourceType = SourceType.valueOf(payloadParts[1]);

            return Optional.of(new SourceIdentity(sourceId, sourceType));
        } catch (Exception e) {
            log.debug("API key validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
