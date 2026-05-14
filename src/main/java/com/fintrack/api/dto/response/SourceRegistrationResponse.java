package com.fintrack.api.dto.response;

import com.fintrack.api.domain.SourceStatus;
import com.fintrack.common.domain.SourceType;

import java.time.Instant;
import java.util.UUID;

public record SourceRegistrationResponse(
        UUID sourceId,
        SourceType sourceType,
        SourceStatus status,
        Instant registeredAt
) {}
