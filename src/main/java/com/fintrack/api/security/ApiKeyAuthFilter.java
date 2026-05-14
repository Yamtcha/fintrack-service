package com.fintrack.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.domain.SourceStatus;
import com.fintrack.api.domain.entity.ApiKey;
import com.fintrack.api.domain.repository.ApiKeyRepository;
import com.fintrack.api.domain.repository.SourceRepository;
import com.fintrack.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int PREFIX_LENGTH = 8;

    private final ApiKeyRepository apiKeyRepository;
    private final SourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = authHeader.substring(BEARER_PREFIX.length()).trim();
        Optional<SourceIdentity> identity = validateApiKey(rawKey);

        if (identity.isEmpty()) {
            sendUnauthorized(response, "Invalid or expired API key");
            return;
        }

        SourceIdentity sourceIdentity = identity.get();
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_SOURCE"),
                new SimpleGrantedAuthority("SCOPE_" + sourceIdentity.scope().name())
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(sourceIdentity, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private Optional<SourceIdentity> validateApiKey(String rawKey) {
        if (rawKey.length() < PREFIX_LENGTH) {
            return Optional.empty();
        }

        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByPrefixAndActiveTrue(prefix);
        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiKey apiKey = apiKeyOpt.get();

        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        String hash = sha256Hex(rawKey);
        if (!hash.equals(apiKey.getKeyHash())) {
            return Optional.empty();
        }

        return sourceRepository.findByIdAndStatus(apiKey.getSourceId(), SourceStatus.ACTIVE)
                .map(source -> new SourceIdentity(
                        source.getId(),
                        source.getName(),
                        source.getSourceType(),
                        apiKey.getScope()
                ));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(
                "UNAUTHORIZED",
                message,
                UUID.randomUUID().toString(),
                Instant.now()
        );
        objectMapper.writeValue(response.getWriter(), error);
    }
}
