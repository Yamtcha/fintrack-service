package com.fintrack.api.service;

import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    SyncJobRepository syncJobRepository;

    @Mock
    BatchProcessorService batchProcessorService;

    @InjectMocks
    IngestionService ingestionService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ingest_withExistingIdempotencyKey_returnsExistingJob() {
        UUID sourceId = UUID.randomUUID();
        SourceIdentity identity = new SourceIdentity(sourceId, SourceType.CREDIT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null));

        SyncJob existing = SyncJob.builder()
                .id(UUID.randomUUID())
                .batchId("batch-1")
                .sourceId(sourceId)
                .status(SyncJobStatus.PENDING)
                .totalReceived(1)
                .idempotencyKey("key-123")
                .build();

        when(syncJobRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));

        TransactionDto tx = new TransactionDto("ext-1", 100L, "ZAR", "merchant", "desc", Instant.now(), Map.of());
        TransactionIngestionRequest request = new TransactionIngestionRequest("batch-1", List.of(tx));

        IngestionResponse response = ingestionService.ingest(request, "key-123");

        assertThat(response).isNotNull();
        assertThat(response.batchId()).isEqualTo(existing.getBatchId());
        assertThat(response.jobId()).isEqualTo(existing.getId());

        verify(syncJobRepository, never()).save(any());
        verify(batchProcessorService, never()).process(any(), any(), any());
    }

    @Test
    void ingest_createsNewJobAndSavesIdempotencyKey() {
        UUID sourceId = UUID.randomUUID();
        SourceIdentity identity = new SourceIdentity(sourceId, SourceType.CREDIT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null));

        when(syncJobRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(syncJobRepository.findByBatchIdAndSourceId("batch-2", sourceId)).thenReturn(Optional.empty());

        // capture saved job
        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        SyncJob saved = SyncJob.builder()
                .id(UUID.randomUUID())
                .batchId("batch-2")
                .sourceId(sourceId)
                .status(SyncJobStatus.PENDING)
                .totalReceived(1)
                .idempotencyKey("new-key")
                .build();
        when(syncJobRepository.save(any())).thenReturn(saved);

        TransactionDto tx = new TransactionDto("ext-2", 200L, "ZAR", null, null, Instant.now(), Map.of());
        TransactionIngestionRequest request = new TransactionIngestionRequest("batch-2", List.of(tx));

        IngestionResponse response = ingestionService.ingest(request, "new-key");

        assertThat(response).isNotNull();
        assertThat(response.batchId()).isEqualTo("batch-2");
        assertThat(response.jobId()).isEqualTo(saved.getId());

        verify(syncJobRepository).save(captor.capture());
        SyncJob toSave = captor.getValue();
        assertThat(toSave.getIdempotencyKey()).isEqualTo("new-key");

        verify(batchProcessorService).process(eq(saved.getId()), eq(request), any());
    }
}

