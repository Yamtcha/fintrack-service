package com.fintrack.api.controller;

import com.fintrack.api.domain.entity.Source;
import com.fintrack.api.dto.request.SourceRegistrationRequest;
import com.fintrack.api.dto.response.SourceRegistrationResponse;
import com.fintrack.api.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/sources")
@RequiredArgsConstructor
@Tag(name = "Sources", description = "Source registration and management")
public class SourceController {

    private final SourceService sourceService;

    @PostMapping("/register")
    @Operation(summary = "Register a new source system")
    public ResponseEntity<SourceRegistrationResponse> register(
            @Valid @RequestBody SourceRegistrationRequest request) {
        log.error("Source registration request received name={} type={}", request.name(), request.sourceType());
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.register(request));
    }

    @GetMapping("/{sourceId}")
    @Operation(summary = "Get source details")
    public ResponseEntity<Source> getSource(@PathVariable UUID sourceId) {
        log.error("Source lookup requested sourceId={}", sourceId);
        return ResponseEntity.ok(sourceService.getSource(sourceId));
    }

    @DeleteMapping("/{sourceId}")
    @Operation(summary = "Deactivate a source (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID sourceId) {
        log.error("Source deactivation requested sourceId={}", sourceId);
        sourceService.deactivate(sourceId);
        return ResponseEntity.noContent().build();
    }
}
