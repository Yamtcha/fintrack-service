package com.fintrack.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.config.SecurityConfig;
import com.fintrack.api.domain.SourceStatus;
import com.fintrack.api.domain.entity.Source;
import com.fintrack.api.dto.request.SourceRegistrationRequest;
import com.fintrack.api.dto.response.SourceRegistrationResponse;
import com.fintrack.api.security.ApiKeyService;
import com.fintrack.api.service.SourceService;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.exception.SourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourceController.class)
@Import(SecurityConfig.class)
class SourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SourceService sourceService;

    @MockBean
    private ApiKeyService apiKeyService;

    @Test
    void register_validRequest_returns201() throws Exception {
        SourceRegistrationRequest request = new SourceRegistrationRequest(
                "Chase Checking", SourceType.DEBIT);

        UUID sourceId = UUID.randomUUID();
        SourceRegistrationResponse response = new SourceRegistrationResponse(
                sourceId, SourceType.DEBIT, SourceStatus.ACTIVE, Instant.now(), "test-api-key");

        when(sourceService.register(any())).thenReturn(response);

        mockMvc.perform(post("/v1/sources/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void register_missingName_returns400() throws Exception {
        SourceRegistrationRequest request = new SourceRegistrationRequest(
                "", SourceType.DEBIT);

        mockMvc.perform(post("/v1/sources/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getSource_existingId_returns200() throws Exception {
        UUID sourceId = UUID.randomUUID();
        Source source = Source.builder()
                .id(sourceId)
                .name("Chase Checking")
                .sourceType(SourceType.DEBIT)
                .status(SourceStatus.ACTIVE)
                .build();

        when(sourceService.getSource(sourceId)).thenReturn(source);

        mockMvc.perform(get("/v1/sources/{sourceId}", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chase Checking"));
    }

    @Test
    @WithMockUser
    void getSource_unknownId_returns404() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(sourceService.getSource(sourceId))
                .thenThrow(new SourceNotFoundException("Source not found: " + sourceId));

        mockMvc.perform(get("/v1/sources/{sourceId}", sourceId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void deactivate_existingSource_returns204() throws Exception {
        UUID sourceId = UUID.randomUUID();
        doNothing().when(sourceService).deactivate(sourceId);

        mockMvc.perform(delete("/v1/sources/{sourceId}", sourceId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(sourceService).deactivate(sourceId);
    }
}