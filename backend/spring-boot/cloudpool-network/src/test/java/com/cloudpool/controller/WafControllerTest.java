package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.model.WafRule;
import com.cloudpool.repository.WafRuleRepository;
import com.cloudpool.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WafControllerTest {

    @Mock private WafRuleRepository wafRuleRepository;
    @Mock private ProjectService projectService;

    @InjectMocks
    private WafController wafController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID projectId;
    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(wafController).build();

        projectId = UUID.randomUUID();
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void getRules_shouldReturnRules() throws Exception {
        WafRule rule = new WafRule();
        rule.setId(UUID.randomUUID());
        rule.setProjectId(projectId);
        rule.setRuleType("IP_BLOCK");
        rule.setPattern("192.168.1.1");
        rule.setActive(true);

        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId))
                .thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/projects/{projectId}/waf", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleType").value("IP_BLOCK"))
                .andExpect(jsonPath("$[0].pattern").value("192.168.1.1"));

        verify(projectService).getProject(projectId, testUser.getId());
    }

    @Test
    void addRule_shouldCreateRule() throws Exception {
        WafRule savedRule = new WafRule();
        savedRule.setId(UUID.randomUUID());
        savedRule.setProjectId(projectId);
        savedRule.setRuleType("RATE_LIMIT");
        savedRule.setPattern("5.0");
        savedRule.setAction("BLOCK");
        savedRule.setActive(true);

        when(wafRuleRepository.save(any(WafRule.class))).thenReturn(savedRule);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("ruleType", "RATE_LIMIT", "pattern", "5.0", "action", "block"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/waf", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleType").value("RATE_LIMIT"))
                .andExpect(jsonPath("$.pattern").value("5.0"))
                .andExpect(jsonPath("$.action").value("BLOCK"));

        verify(projectService).getProject(projectId, testUser.getId());
    }

    @Test
    void addRule_withoutAction_shouldDefaultToBlock() throws Exception {
        WafRule savedRule = new WafRule();
        savedRule.setId(UUID.randomUUID());
        savedRule.setProjectId(projectId);
        savedRule.setRuleType("SQLI_BLOCK");
        savedRule.setPattern("select");
        savedRule.setAction("BLOCK");
        savedRule.setActive(true);

        when(wafRuleRepository.save(any(WafRule.class))).thenReturn(savedRule);

        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of("ruleType", "SQLI_BLOCK", "pattern", "select"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/waf", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("BLOCK"));
    }

    @Test
    void deleteRule_shouldDeleteRule() throws Exception {
        UUID ruleId = UUID.randomUUID();
        WafRule rule = new WafRule();
        rule.setId(ruleId);
        rule.setProjectId(projectId);

        when(wafRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/waf/{ruleId}", projectId, ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("WAF rule deleted successfully"));

        verify(wafRuleRepository).delete(rule);
    }

    @Test
    void deleteRule_wrongProject_shouldReturn403() throws Exception {
        UUID ruleId = UUID.randomUUID();
        WafRule rule = new WafRule();
        rule.setId(ruleId);
        rule.setProjectId(UUID.randomUUID());

        when(wafRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/waf/{ruleId}", projectId, ruleId))
                .andExpect(status().isForbidden());

        verify(wafRuleRepository, never()).delete(any());
    }

    @Test
    void deleteRule_notFound_shouldReturn400() throws Exception {
        UUID ruleId = UUID.randomUUID();
        when(wafRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/projects/{projectId}/waf/{ruleId}", projectId, ruleId))
                .andExpect(status().isBadRequest());
    }
}
