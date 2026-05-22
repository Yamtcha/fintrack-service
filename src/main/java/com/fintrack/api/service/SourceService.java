package com.fintrack.api.service;

import com.fintrack.api.domain.SourceStatus;
import com.fintrack.api.domain.entity.Source;
import com.fintrack.api.domain.repository.SourceRepository;
import com.fintrack.api.dto.request.SourceRegistrationRequest;
import com.fintrack.api.dto.response.SourceRegistrationResponse;
import com.fintrack.api.security.ApiKeyService;
import com.fintrack.common.exception.SourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;
    private final ApiKeyService apiKeyService;

    @Transactional
    public SourceRegistrationResponse register(SourceRegistrationRequest request) {
        // ── Build and save source ─────────────────────────────────────────────
        Source source = Source.builder()
                .name(request.name())
                .sourceType(request.sourceType())
                .status(SourceStatus.ACTIVE)
                .build();

        Source savedSource = sourceRepository.save(source);
        log.info("Registered source id={} name={} type={}",
                savedSource.getId(), savedSource.getName(), savedSource.getSourceType());

        // ── Generate API key ──────────────────────────────────────────────────
        String apiKey = apiKeyService.generateKey(savedSource.getId(), savedSource.getSourceType());

        return new SourceRegistrationResponse(
                savedSource.getId(),
                savedSource.getSourceType(),
                savedSource.getStatus(),
                savedSource.getRegisteredAt(),
                apiKey
        );
    }

    @Transactional(readOnly = true)
    public Source getSource(UUID sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("Source not found: " + sourceId));
    }

    @Transactional
    public void deactivate(UUID sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("Source not found: " + sourceId));
        source.setStatus(SourceStatus.INACTIVE);
        sourceRepository.save(source);
        log.info("Deactivated source id={}", sourceId);
    }
}
