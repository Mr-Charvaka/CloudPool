package com.cloudpool.controller;

import com.cloudpool.handler.PubSubWebSocketHandler;
import com.cloudpool.model.User;
import com.cloudpool.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PubSubControllerTest {

    @Mock private PubSubWebSocketHandler pubSubHandler;
    @Mock private ProjectService projectService;

    @InjectMocks
    private PubSubController pubSubController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(pubSubController).build();

        projectId = UUID.randomUUID();
        User testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void broadcast_shouldSucceed() throws Exception {
        var request = java.util.Map.of(
                "channel", "my-channel",
                "payloadJson", "{\"text\":\"hello\"}");

        mockMvc.perform(post("/api/v1/projects/{projectId}/pubsub/broadcast", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Broadcasted successfully"));

        verify(pubSubHandler).broadcast(anyString(), anyString());
    }

    @Test
    void broadcast_handlerThrows_shouldReturn400() throws Exception {
        doThrow(new RuntimeException("Broadcast failed"))
                .when(pubSubHandler).broadcast(anyString(), anyString());

        var request = java.util.Map.of(
                "channel", "my-channel",
                "payloadJson", "{}");

        mockMvc.perform(post("/api/v1/projects/{projectId}/pubsub/broadcast", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void broadcast_missingChannel_shouldFailValidation() throws Exception {
        var request = java.util.Map.of("payloadJson", "{}");

        mockMvc.perform(post("/api/v1/projects/{projectId}/pubsub/broadcast", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
