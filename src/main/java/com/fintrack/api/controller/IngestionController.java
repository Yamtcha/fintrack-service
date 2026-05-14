package com.fintrack.api.controller;

import com.fintrack.api.domain.entity.SyncJob;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.api.service.IngestionService;
import com.fintrack.api.domain.SyncJobStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/sources")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "Transaction batch ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/transactions")
    @Operation(summary = "Submit a batch of transactions for async processing")
    public ResponseEntity<IngestionResponse> ingest(
            @Valid @RequestBody TransactionIngestionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(request));
    }

    @GetMapping("/sync/status/{batchId}")
    @Operation(summary = "Get sync job status for a batch")
    public ResponseEntity<SyncJob> getSyncStatus(@PathVariable String batchId) {
        return ResponseEntity.ok(ingestionService.getSyncJobByBatchId(batchId));
    }

    @GetMapping("/sync/history")
    @Operation(summary = "Get paginated sync history for the authenticated source")
    public ResponseEntity<Page<SyncJob>> getSyncHistory(
            @AuthenticationPrincipal SourceIdentity identity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SyncJobStatus status) {
        return ResponseEntity.ok(
                ingestionService.getSyncHistory(identity.sourceId(), status, PageRequest.of(page, size)));
    }
}
