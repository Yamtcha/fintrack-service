package com.fintrack.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TransactionIngestionRequest(

        @NotBlank(message = "Batch ID must not be blank")
        String batchId,

        @NotEmpty(message = "Transactions list must not be empty")
        @Size(max = 1000, message = "Batch size must not exceed 1000 transactions")
        @Valid
        List<RawTransactionDto> transactions
) {}
