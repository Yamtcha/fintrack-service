package com.fintrack.api.config;

import com.fintrack.api.security.ApiKeyService;
import com.fintrack.api.security.SourceIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final String masterKey;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, @Value("${api.key}") String masterKey) {
        this.apiKeyService = apiKeyService;
        this.masterKey = masterKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestApiKey = request.getHeader(API_KEY_HEADER);
        if (requestApiKey == null) {
            reject(response);
            return;
        }

        Optional<SourceIdentity> identity = apiKeyService.validate(requestApiKey);
        if (identity.isPresent()) {
            var auth = new UsernamePasswordAuthenticationToken(identity.get(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } else if (masterKey.equals(requestApiKey)) {
            var auth = new UsernamePasswordAuthenticationToken("api-client", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } else {
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        log.error("Unauthorized request rejected");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
