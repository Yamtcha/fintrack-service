package com.fintrack.api.controller;

import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ingestion")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ingestion", description = "Transaction batch ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/transactions")
    @Operation(summary = "Submit a batch of transactions for async processing")
    public ResponseEntity<IngestionResponse> ingest(
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 255, message = "Idempotency-Key must not exceed 255 characters")
            @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency-Key contains invalid characters")
            String idempotencyKey,
            @Valid @RequestBody TransactionIngestionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(request, idempotencyKey));
    }

    @GetMapping("/sync/status/{batchId}")
    @Operation(summary = "Get sync job status for a batch")
    public ResponseEntity<SyncJob> getSyncStatus(@PathVariable String batchId) {
        return ResponseEntity.ok(ingestionService.getSyncJobByBatchId(batchId));
    }
}
