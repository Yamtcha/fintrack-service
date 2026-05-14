package com.fintrack.api.domain.entity;

import com.fintrack.api.domain.SyncJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SyncJobStatus status = SyncJobStatus.PENDING;

    @Column(name = "total_received", nullable = false)
    private int totalReceived;

    @Column(name = "total_processed", nullable = false)
    @Builder.Default
    private int totalProcessed = 0;

    @Column(name = "total_failed", nullable = false)
    @Builder.Default
    private int totalFailed = 0;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "started_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
