package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.model.WafRule;
import com.cloudpool.repository.WafRuleRepository;
import com.cloudpool.service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/waf")
@RequiredArgsConstructor

public class WafController {

    private final WafRuleRepository wafRuleRepository;
    private final ProjectService projectService;

    private void validateProjectAccess(UUID projectId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        projectService.getProject(projectId, user.getId()); // Throws if unauthorized
    }

    @GetMapping
    public ResponseEntity<?> getRules(@PathVariable UUID projectId) {
        validateProjectAccess(projectId);
        return ResponseEntity.ok(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId));
    }

    @PostMapping
    public ResponseEntity<?> addRule(
            @PathVariable UUID projectId,
            @Valid @RequestBody WafRuleRequest request) {
        validateProjectAccess(projectId);
        
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType(request.getRuleType().toUpperCase());
        rule.setPattern(request.getPattern());
        rule.setAction(request.getAction() != null ? request.getAction().toUpperCase() : "BLOCK");
        rule.setActive(true);

        WafRule saved = wafRuleRepository.save(rule);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<?> deleteRule(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId) {
        validateProjectAccess(projectId);
        
        WafRule rule = wafRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        
        if (!rule.getProjectId().equals(projectId)) {
            return ResponseEntity.status(403).build();
        }

        wafRuleRepository.delete(rule);
        return ResponseEntity.ok(Map.of("message", "WAF rule deleted successfully"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @Data
    public static class WafRuleRequest {
        private String ruleType; // IP_BLOCK, RATE_LIMIT, SQLI_BLOCK
        private String pattern;
        private String action;
    }
}
