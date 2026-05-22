package com.fintrack.api.service;

import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.domain.repository.SyncJobRepository;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.exception.SourceNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    SyncJobRepository syncJobRepository;

    @Mock
    BatchProcessorService batchProcessorService;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(syncJobRepository, batchProcessorService, new SimpleMeterRegistry());
    }

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
        assertThat(response.status()).isEqualTo(SyncJobStatus.PENDING);
        assertThat(response.totalReceived()).isEqualTo(1);

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
        assertThat(response.status()).isEqualTo(SyncJobStatus.PENDING);
        assertThat(response.totalReceived()).isEqualTo(1);

        verify(syncJobRepository).save(captor.capture());
        SyncJob toSave = captor.getValue();
        assertThat(toSave.getIdempotencyKey()).isEqualTo("new-key");

        verify(batchProcessorService).process(eq(saved), eq(request), any());
    }

    @Test
    void ingest_withDuplicateBatch_returnsExistingJobWithoutReprocessing() {
        UUID sourceId = UUID.randomUUID();
        SourceIdentity identity = new SourceIdentity(sourceId, SourceType.DEBIT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null));

        SyncJob existing = SyncJob.builder()
                .id(UUID.randomUUID())
                .batchId("dup-batch")
                .sourceId(sourceId)
                .status(SyncJobStatus.COMPLETED)
                .totalReceived(2)
                .build();

        when(syncJobRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(syncJobRepository.findByBatchIdAndSourceId("dup-batch", sourceId)).thenReturn(Optional.of(existing));

        TransactionDto tx = new TransactionDto("ext-3", 500L, "ZAR", "shop", "desc", Instant.now(), Map.of());
        TransactionIngestionRequest request = new TransactionIngestionRequest("dup-batch", List.of(tx));

        IngestionResponse response = ingestionService.ingest(request, "some-key");

        assertThat(response.batchId()).isEqualTo("dup-batch");
        assertThat(response.jobId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(response.totalReceived()).isEqualTo(2);

        verify(syncJobRepository, never()).save(any());
        verify(batchProcessorService, never()).process(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void ingest_withBlankOrNullIdempotencyKey_skipsIdempotencyCheckAndSavesJobWithoutKey(String idempotencyKey) {
        UUID sourceId = UUID.randomUUID();
        SourceIdentity identity = new SourceIdentity(sourceId, SourceType.LOANS);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null));

        when(syncJobRepository.findByBatchIdAndSourceId("batch-3", sourceId)).thenReturn(Optional.empty());

        SyncJob saved = SyncJob.builder()
                .id(UUID.randomUUID())
                .batchId("batch-3")
                .sourceId(sourceId)
                .status(SyncJobStatus.PENDING)
                .totalReceived(1)
                .build();
        when(syncJobRepository.save(any())).thenReturn(saved);

        TransactionDto tx = new TransactionDto("ext-4", 300L, "ZAR", null, null, Instant.now(), Map.of());
        TransactionIngestionRequest request = new TransactionIngestionRequest("batch-3", List.of(tx));

        IngestionResponse response = ingestionService.ingest(request, idempotencyKey);

        assertThat(response.jobId()).isEqualTo(saved.getId());

        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        verify(syncJobRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isNull();

        verify(syncJobRepository, never()).findByIdempotencyKey(any());
        verify(batchProcessorService).process(eq(saved), eq(request), any());
    }

    @Test
    void ingest_withNonSourceIdentityPrincipal_throwsAuthenticationException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("unknown-principal", null));

        TransactionDto tx = new TransactionDto("ext-5", 100L, "ZAR", null, null, Instant.now(), Map.of());
        TransactionIngestionRequest request = new TransactionIngestionRequest("batch-4", List.of(tx));

        assertThatThrownBy(() -> ingestionService.ingest(request, "key"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("No source identity in security context");
    }

    @Test
    void getSyncJobByBatchId_existingBatch_returnsJob() {
        SyncJob job = SyncJob.builder()
                .id(UUID.randomUUID())
                .batchId("batch-ok")
                .status(SyncJobStatus.COMPLETED)
                .totalReceived(3)
                .build();

        when(syncJobRepository.findByBatchId("batch-ok")).thenReturn(Optional.of(job));

        SyncJob result = ingestionService.getSyncJobByBatchId("batch-ok");

        assertThat(result).isEqualTo(job);
        assertThat(result.getBatchId()).isEqualTo("batch-ok");
        assertThat(result.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
    }

    @Test
    void getSyncJobByBatchId_unknownBatch_throwsSourceNotFoundException() {
        when(syncJobRepository.findByBatchId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.getSyncJobByBatchId("missing"))
                .isInstanceOf(SourceNotFoundException.class)
                .hasMessageContaining("missing");
    }
}
