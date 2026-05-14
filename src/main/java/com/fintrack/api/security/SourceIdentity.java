package com.fintrack.api.security;

import com.fintrack.api.domain.ApiKeyScope;
import com.fintrack.common.domain.SourceType;

import java.util.UUID;

public record SourceIdentity(
        UUID sourceId,
        String sourceName,
        SourceType sourceType,
        ApiKeyScope scope
) {}
