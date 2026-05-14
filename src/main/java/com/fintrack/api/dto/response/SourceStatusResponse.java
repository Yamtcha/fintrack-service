package com.fintrack.api.dto.response;

import com.fintrack.api.domain.SourceStatus;

import java.time.Instant;
import java.util.UUID;

public record SourceStatusResponse(
        UUID sourceId,
        SourceStatus status,
        Instant lastSync,
        long totalBatchesProcessed,
        long totalTransactionsProcessed
) {}
