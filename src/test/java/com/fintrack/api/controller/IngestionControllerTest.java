package com.fintrack.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.config.SecurityConfig;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "api.key=test-key")
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestionService ingestionService;

    @Test
    void ingest_validBatch_returns202() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS SANDTON", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of(transaction));

        IngestionResponse response = new IngestionResponse(
                "BATCH-001", UUID.randomUUID(), SyncJobStatus.PENDING, 1, "Batch accepted for processing");

        when(ingestionService.ingest(any(), any())).thenReturn(response);

        mockMvc.perform(post("/v1/ingestion/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").value("BATCH-001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.message").value("Batch accepted for processing"));
    }

    @Test
    void ingest_emptyBatch_returns400() throws Exception {
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of());

        mockMvc.perform(post("/v1/ingestion/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_missingBatchId_returns400() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("", List.of(transaction));

        mockMvc.perform(post("/v1/ingestion/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_unauthenticated_returns401() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of(transaction));

        mockMvc.perform(post("/v1/ingestion/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
