package com.fintrack.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

public record TransactionDto(

        @NotBlank(message = "External ID must not be blank")
        String externalId,

        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be positive")
        Long amount,

        @NotBlank(message = "Currency must not be blank")
        String currency,

        String merchantName,

        String description,

        @NotNull(message = "Transaction timestamp must not be null")
        Instant transactedAt,

        Map<String, Object> metadata
) {}
