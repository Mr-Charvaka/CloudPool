package com.cloudpool.controller;

import com.cloudpool.model.OutboxEmail;
import com.cloudpool.model.ReceivedEmail;
import com.cloudpool.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/emails")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EmailController {

    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<List<OutboxEmail>> getEmails() {
        return ResponseEntity.ok(emailService.getOutboxEmails());
    }

    @GetMapping("/inbox")
    public ResponseEntity<List<ReceivedEmail>> getReceivedEmails() {
        return ResponseEntity.ok(emailService.getReceivedEmails());
    }

    @DeleteMapping("/inbox")
    public ResponseEntity<?> clearInbox() {
        emailService.clearInbox();
        return ResponseEntity.ok(Map.of("message", "Received emails cleared successfully"));
    }

    @PostMapping("/send-test")
    public ResponseEntity<?> sendTestEmail(@Valid @RequestBody TestEmailRequest request) {
        if (request.getTo() == null || request.getTo().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient email address ('to') is required"));
        }
        String subject = request.getSubject() != null ? request.getSubject() : "Test mail from CloudPool";
        String body = request.getBody() != null ? request.getBody() : "Hello from your self-hosted CloudPool instance!";

        OutboxEmail email = emailService.sendEmail(request.getTo(), subject, body);
        return ResponseEntity.ok(email);
    }

    @DeleteMapping
    public ResponseEntity<?> clearOutbox() {
        emailService.clearOutbox();
        return ResponseEntity.ok(Map.of("message", "Outbox logs cleared successfully"));
    }

    @PostMapping("/send-direct")
    public ResponseEntity<?> sendDirectEmail(@Valid @RequestBody TestEmailRequest request) {
        if (request.getTo() == null || request.getTo().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient email address ('to') is required"));
        }
        String subject = request.getSubject() != null ? request.getSubject() : "Hello from CloudPool ☁️";
        String body = request.getBody() != null ? request.getBody()
                : "This email was sent directly from a CloudPool self-hosted server via MX record delivery. No third-party API keys were used!";

        OutboxEmail email = emailService.sendDirectEmail(request.getTo(), subject, body);
        return ResponseEntity.ok(email);
    }

    @Data
    public static class TestEmailRequest {
        private String to;
        @jakarta.validation.constraints.NotBlank
        private String subject;
        @jakarta.validation.constraints.NotBlank
        private String body;
    }
}
