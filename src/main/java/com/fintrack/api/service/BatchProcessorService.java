package com.fintrack.api.service;

import com.fintrack.api.adapter.AdapterRegistry;
import com.fintrack.api.adapter.TransactionAdapter;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.model.Transaction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessorService {

    private final SyncJobRepository syncJobRepository;
    private final AdapterRegistry adapterRegistry;
    private final PublisherService publisherService;
    private final MeterRegistry meterRegistry;

    @Async
    @Transactional
    public void process(SyncJob job, TransactionIngestionRequest request, SourceIdentity identity) {

        job.setStatus(SyncJobStatus.PROCESSING);
        syncJobRepository.save(job);

        Timer.Sample sample = Timer.start(meterRegistry);
        TransactionAdapter adapter = adapterRegistry.getAdapter(identity.sourceType());

        // ── Process each transaction as a separate async task ─────────────────
        List<CompletableFuture<Boolean>> futures = request.transactions().stream()
                .map(raw -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Transaction transaction = adapter.adapt(raw, identity);
                        publisherService.publish(transaction);
                        return true;
                    } catch (Exception e) {
                        log.error("Failed to process transaction externalId={} batchId={}: {}",
                                raw.externalId(), request.batchId(), e.getMessage(), e);
                        return false;
                    }
                }))
                .toList();

        // ── Wait for ALL to finish then update job ────────────────────────────
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long processed = futures.stream().filter(CompletableFuture::join).count();
            long failed = futures.size() - processed;

            job.setTotalProcessed((int) processed);
            job.setTotalFailed((int) failed);
            job.setCompletedAt(Instant.now());

            if (failed == 0) {
                job.setStatus(SyncJobStatus.COMPLETED);
            } else if (processed > 0) {
                job.setStatus(SyncJobStatus.PARTIAL_FAILURE);
            } else {
                job.setStatus(SyncJobStatus.FAILED);
            }

            syncJobRepository.save(job);

            // ── Metrics ───────────────────────────────────────────────────────
            sample.stop(Timer.builder("fintrack.ingestion.batch.duration")
                    .tag("source_type", identity.sourceType().name())
                    .tag("status", job.getStatus().name())
                    .register(meterRegistry));

            meterRegistry.counter("fintrack.ingestion.transactions",
                            "source_type", identity.sourceType().name(),
                            "result", "processed")
                    .increment(processed);

            meterRegistry.counter("fintrack.ingestion.transactions",
                            "source_type", identity.sourceType().name(),
                            "result", "failed")
                    .increment(failed);

            log.info("Batch completed batchId={} status={} processed={} failed={}",
                    request.batchId(), job.getStatus(), processed, failed);

        } catch (Exception err) {
            log.error("Batch failed entirely batchId={}: {}", request.batchId(), err.getMessage(), err);
            job.setStatus(SyncJobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            syncJobRepository.save(job);
        }
    }
}