package com.fintrack.api.dto.response;

import com.fintrack.api.domain.SyncJobStatus;

import java.util.UUID;

public record IngestionResponse(
        String batchId,
        UUID jobId,
        SyncJobStatus status,
        int totalReceived,
        String message
) {}
