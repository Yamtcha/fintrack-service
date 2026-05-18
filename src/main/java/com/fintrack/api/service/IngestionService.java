package com.fintrack.api.service;

import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.exception.SourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SyncJobRepository syncJobRepository;
    private final BatchProcessorService batchProcessorService;

    @Transactional
    public IngestionResponse ingest(TransactionIngestionRequest request, String idempotencyKey) {
        SourceIdentity identity = resolveIdentity();

        // If an idempotency key was provided, try to return the existing job (global unique key)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<SyncJob> byKey = syncJobRepository.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) {
                log.info("Idempotent request detected idempotencyKey={} returning existing job id={}",
                        idempotencyKey, byKey.get().getId());
                return toResponse(byKey.get());
            }
        }

        Optional<SyncJob> existing = syncJobRepository.findByBatchIdAndSourceId(
                request.batchId(), identity.sourceId());
        if (existing.isPresent()) {
            log.info("Duplicate batch batchId={} sourceId={}, returning existing job",
                    request.batchId(), identity.sourceId());
            return toResponse(existing.get());
        }

        SyncJob.SyncJobBuilder jobBuilder = SyncJob.builder()
                .batchId(request.batchId())
                .sourceId(identity.sourceId())
                .status(SyncJobStatus.PENDING)
                .totalReceived(request.transactions().size());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            jobBuilder.idempotencyKey(idempotencyKey);
        }

        SyncJob job = jobBuilder.build();

        job = syncJobRepository.save(job);
        batchProcessorService.process(job.getId(), request, identity);

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public SyncJob getSyncJobByBatchId(String batchId) {
        return syncJobRepository.findByBatchId(batchId)
                .orElseThrow(() -> new SourceNotFoundException("No sync job found for batchId: " + batchId));
    }

    private SourceIdentity resolveIdentity() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SourceIdentity identity) {
            return identity;
        }
        throw new AuthenticationCredentialsNotFoundException("No source identity in security context");
    }

    private IngestionResponse toResponse(SyncJob job) {
        return new IngestionResponse(
                job.getBatchId(),
                job.getId(),
                job.getStatus(),
                job.getTotalReceived(),
                "Batch accepted for processing"
        );
    }
}
