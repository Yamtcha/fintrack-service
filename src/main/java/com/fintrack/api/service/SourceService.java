package com.fintrack.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.domain.entity.Source;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SourceRepository;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.SourceRegistrationRequest;
import com.fintrack.api.dto.response.SourceRegistrationResponse;
import com.fintrack.api.dto.response.SourceStatusResponse;
import com.fintrack.api.domain.SourceStatus;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.common.exception.SourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;
    private final SyncJobRepository syncJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SourceRegistrationResponse register(SourceRegistrationRequest request) {
        String configJson = serializeConfig(request.config());

        Source source = Source.builder()
                .name(request.name())
                .sourceType(request.sourceType())
                .config(configJson)
                .status(SourceStatus.ACTIVE)
                .build();

        source = sourceRepository.save(source);
        log.info("Registered source id={} name={} type={}", source.getId(), source.getName(), source.getSourceType());

        return new SourceRegistrationResponse(
                source.getId(),
                source.getSourceType(),
                source.getStatus(),
                source.getRegisteredAt()
        );
    }

    @Transactional(readOnly = true)
    public Source getSource(UUID sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("Source not found: " + sourceId));
    }

    @Transactional(readOnly = true)
    public SourceStatusResponse getStatus(UUID sourceId) {
        Source source = getSource(sourceId);

        List<SyncJob> completedJobs = syncJobRepository.findBySourceId(sourceId, Pageable.unpaged())
                .stream()
                .filter(j -> j.getStatus() == SyncJobStatus.COMPLETED
                          || j.getStatus() == SyncJobStatus.PARTIAL_FAILURE)
                .toList();

        long totalBatches = completedJobs.size();
        long totalTransactions = completedJobs.stream().mapToLong(SyncJob::getTotalProcessed).sum();

        Instant lastSync = completedJobs.stream()
                .map(SyncJob::getCompletedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new SourceStatusResponse(
                source.getId(),
                source.getStatus(),
                lastSync,
                totalBatches,
                totalTransactions
        );
    }

    @Transactional
    public void deactivate(UUID sourceId) {
        Source source = getSource(sourceId);
        source.setStatus(SourceStatus.INACTIVE);
        sourceRepository.save(source);
        log.info("Deactivated source id={}", sourceId);
    }

    private String serializeConfig(Object config) {
        if (config == null) return null;
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid config payload", e);
        }
    }
}
