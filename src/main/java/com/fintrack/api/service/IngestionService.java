package com.fintrack.api.service;

import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.RawTransactionDto;
import com.fintrack.api.adapter.AdapterRegistry;
import com.fintrack.api.adapter.TransactionAdapter;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.common.exception.SourceNotFoundException;
import com.fintrack.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SyncJobRepository syncJobRepository;
    private final AdapterRegistry adapterRegistry;
    private final PublisherService publisherService;

    @Transactional
    public IngestionResponse ingest(TransactionIngestionRequest request) {
        SourceIdentity identity = resolveIdentity();

        Optional<SyncJob> existing = syncJobRepository.findByBatchIdAndSourceId(
                request.batchId(), identity.sourceId());
        if (existing.isPresent()) {
            log.info("Duplicate batch batchId={} sourceId={}, returning existing job",
                    request.batchId(), identity.sourceId());
            return toResponse(existing.get());
        }

        SyncJob job = SyncJob.builder()
                .batchId(request.batchId())
                .sourceId(identity.sourceId())
                .status(SyncJobStatus.PENDING)
                .totalReceived(request.transactions().size())
                .build();

        job = syncJobRepository.save(job);
        processAsync(job.getId(), request, identity);

        return toResponse(job);
    }

    @Async
    public void processAsync(UUID jobId, TransactionIngestionRequest request, SourceIdentity identity) {
        SyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("SyncJob not found: " + jobId));

        job.setStatus(SyncJobStatus.PROCESSING);
        syncJobRepository.save(job);

        int processed = 0;
        int failed = 0;

        TransactionAdapter adapter = adapterRegistry.getAdapter(identity.sourceType());

        for (RawTransactionDto raw : request.transactions()) {
            try {
                Transaction canonical = adapter.adapt(raw, identity);
                publisherService.publish(canonical);
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to process transaction externalId={} batchId={}: {}",
                        raw.externalId(), request.batchId(), e.getMessage(), e);
            }
        }

        job.setTotalProcessed(processed);
        job.setTotalFailed(failed);
        job.setCompletedAt(Instant.now());

        if (failed == 0) {
            job.setStatus(SyncJobStatus.COMPLETED);
        } else if (processed > 0) {
            job.setStatus(SyncJobStatus.PARTIAL_FAILURE);
        } else {
            job.setStatus(SyncJobStatus.FAILED);
        }

        syncJobRepository.save(job);
        log.info("Batch completed batchId={} status={} processed={} failed={}",
                request.batchId(), job.getStatus(), processed, failed);
    }

    @Transactional(readOnly = true)
    public SyncJob getSyncJobByBatchId(String batchId) {
        return syncJobRepository.findByBatchId(batchId)
                .orElseThrow(() -> new SourceNotFoundException("No sync job found for batchId: " + batchId));
    }

    @Transactional(readOnly = true)
    public Page<SyncJob> getSyncHistory(UUID sourceId, SyncJobStatus status, Pageable pageable) {
        if (status != null) {
            return syncJobRepository.findBySourceIdAndStatus(sourceId, status, pageable);
        }
        return syncJobRepository.findBySourceId(sourceId, pageable);
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
