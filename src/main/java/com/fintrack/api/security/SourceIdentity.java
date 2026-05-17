package com.fintrack.api.security;

import com.fintrack.common.domain.SourceType;

import java.util.UUID;

public record SourceIdentity(
        UUID sourceId,
        SourceType sourceType
) {}
