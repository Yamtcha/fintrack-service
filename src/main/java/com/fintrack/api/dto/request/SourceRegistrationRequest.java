package com.fintrack.api.dto.request;

import com.fintrack.common.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SourceRegistrationRequest(

        @NotBlank(message = "Source name must not be blank")
        String name,

        @NotNull(message = "Source type must not be null")
        SourceType sourceType,

        Map<String, Object> config
) {}
