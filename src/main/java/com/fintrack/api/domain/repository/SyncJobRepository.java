package com.fintrack.api.domain.repository;

import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.domain.entity.SyncJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    Optional<SyncJob> findByBatchIdAndSourceId(String batchId, UUID sourceId);

    Optional<SyncJob> findByBatchId(String batchId);

    Optional<SyncJob> findByIdempotencyKeyAndSourceId(String idempotencyKey, UUID sourceId);

    Optional<SyncJob> findByIdempotencyKey(String idempotencyKey);

    Page<SyncJob> findBySourceId(UUID sourceId, Pageable pageable);

    Page<SyncJob> findBySourceIdAndStatus(UUID sourceId, SyncJobStatus status, Pageable pageable);
}
