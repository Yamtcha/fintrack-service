package com.fintrack.api.service;

import com.fintrack.api.adapter.AdapterRegistry;
import com.fintrack.api.adapter.TransactionAdapter;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessorService {

    private final SyncJobRepository syncJobRepository;
    private final AdapterRegistry adapterRegistry;
    private final PublisherService publisherService;

    @Async
    public void process(UUID jobId, TransactionIngestionRequest request, SourceIdentity identity) {
        SyncJob job = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("SyncJob not found: " + jobId));

        job.setStatus(SyncJobStatus.PROCESSING);
        syncJobRepository.save(job);

        int processed = 0;
        int failed = 0;

        TransactionAdapter adapter = adapterRegistry.getAdapter(identity.sourceType());

        for (TransactionDto raw : request.transactions()) {
            try {
                Transaction transaction = adapter.adapt(raw, identity);
                publisherService.publish(transaction);
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
}
