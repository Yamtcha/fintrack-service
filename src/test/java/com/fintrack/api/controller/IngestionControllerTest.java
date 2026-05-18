package com.fintrack.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.domain.SyncJobStatus;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.dto.request.TransactionIngestionRequest;
import com.fintrack.api.dto.response.IngestionResponse;
import com.fintrack.api.security.JwtService;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.api.service.IngestionService;
import com.fintrack.common.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestionService ingestionService;

    @MockBean
    private JwtService jwtService;

    private UsernamePasswordAuthenticationToken sourceAuth() {
        SourceIdentity identity = new SourceIdentity(UUID.randomUUID(), SourceType.DEBIT);
        return new UsernamePasswordAuthenticationToken(identity, null,
                List.of(new SimpleGrantedAuthority("ROLE_SOURCE")));
    }

    @Test
    void ingest_validBatch_returns202() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS SANDTON", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of(transaction));

        IngestionResponse response = new IngestionResponse(
                "BATCH-001", UUID.randomUUID(), SyncJobStatus.PENDING, 1, "Batch accepted for processing");

        when(ingestionService.ingest(any(), any())).thenReturn(response);

        mockMvc.perform(post("/v1/sources/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(sourceAuth()))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").value("BATCH-001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.message").value("Batch accepted for processing"));
    }

    @Test
    void ingest_emptyBatch_returns400() throws Exception {
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of());

        mockMvc.perform(post("/v1/sources/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(sourceAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_missingBatchId_returns400() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("", List.of(transaction));

        mockMvc.perform(post("/v1/sources/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(sourceAuth()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_unauthenticated_returns403() throws Exception {
        TransactionDto transaction = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS", Instant.now(), null);
        TransactionIngestionRequest request = new TransactionIngestionRequest("BATCH-001", List.of(transaction));

        mockMvc.perform(post("/v1/sources/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
