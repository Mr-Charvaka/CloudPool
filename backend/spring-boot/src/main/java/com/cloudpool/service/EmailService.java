package com.cloudpool.service;

import com.cloudpool.model.OutboxEmail;
import com.cloudpool.repository.OutboxEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final OutboxEmailRepository outboxEmailRepository;

    @Value("${cloudpool.email.smtp-host:smtp.cloudpool-email.com}")
    private String smtpHost;

    @Value("${cloudpool.email.smtp-port:587}")
    private int smtpPort;

    @Value("${cloudpool.email.smtp-username:noreply@cloudpool-email.com}")
    private String smtpUsername;

    @Value("${cloudpool.email.smtp-password:securepassword}")
    private String smtpPassword;

    @Value("${cloudpool.email.smtp-from:noreply@cloudpool-email.com}")
    private String smtpFrom;

    @Value("${cloudpool.email.sandbox-mode:true}")
    private boolean sandboxMode;

    @Transactional
    public OutboxEmail sendEmail(String to, String subject, String body) {
        log.info("Preparing to send email. To: {}, Subject: {}, SandboxMode: {}", to, subject, sandboxMode);

        OutboxEmail outboxEmail = OutboxEmail.builder()
                .toAddress(to)
                .subject(subject)
                .body(body)
                .sentAt(LocalDateTime.now())
                .status("QUEUED")
                .build();

        outboxEmail = outboxEmailRepository.save(outboxEmail);

        if (sandboxMode) {
            log.info("[SANDBOX EMAIL] Stored email ID: {} in sandbox log (no network call)", outboxEmail.getId());
            outboxEmail.setStatus("SENT");
            return outboxEmailRepository.save(outboxEmail);
        }

        try {
            Properties prop = new Properties();
            prop.put("mail.smtp.auth", "true");
            prop.put("mail.smtp.starttls.enable", "true");
            prop.put("mail.smtp.host", smtpHost);
            prop.put("mail.smtp.port", String.valueOf(smtpPort));
            prop.put("mail.smtp.ssl.trust", smtpHost);

            Session session = Session.getInstance(prop, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);

            outboxEmail.setStatus("SENT");
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP: {}", e.getMessage(), e);
            outboxEmail.setStatus("FAILED");
            outboxEmail.setErrorMessage(e.getMessage());
        }

        return outboxEmailRepository.save(outboxEmail);
    }

    public List<OutboxEmail> getOutboxEmails() {
        return outboxEmailRepository.findAll();
    }

    @Transactional
    public void clearOutbox() {
        outboxEmailRepository.deleteAll();
    }
}
