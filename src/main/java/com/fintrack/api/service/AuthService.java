package com.fintrack.api.service;

import com.fintrack.api.domain.repository.SourceCredentialRepository;
import com.fintrack.api.dto.request.TokenRequest;
import com.fintrack.api.dto.response.TokenResponse;
import com.fintrack.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SourceCredentialRepository credentialRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${fintrack.jwt.expiry-hours}")
    private long expiryHours;

    public TokenResponse login(TokenRequest request) {
        var credential = credentialRepository.findByUsernameAndActiveTrue(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(credential);

        return new TokenResponse(token, "Bearer", expiryHours * 3600);
    }
}
