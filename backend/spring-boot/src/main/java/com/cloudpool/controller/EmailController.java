package com.cloudpool.controller;

import com.cloudpool.model.OutboxEmail;
import com.cloudpool.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/emails")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailController {

    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<List<OutboxEmail>> getEmails() {
        return ResponseEntity.ok(emailService.getOutboxEmails());
    }

    @PostMapping("/send-test")
    public ResponseEntity<?> sendTestEmail(@RequestBody TestEmailRequest request) {
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

    @Data
    public static class TestEmailRequest {
        private String to;
        private String subject;
        private String body;
    }
}
