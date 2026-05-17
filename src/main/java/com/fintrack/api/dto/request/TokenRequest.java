package com.fintrack.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank String username,
        @NotBlank String password
) {}
