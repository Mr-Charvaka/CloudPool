package com.cloudpool.service;

import com.cloudpool.model.ReceivedEmail;
import com.cloudpool.repository.ReceivedEmailRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListenerAdapter;
import org.subethamail.smtp.server.SMTPServer;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddedSmtpServer {

    private final ReceivedEmailRepository receivedEmailRepository;

    @Value("${cloudpool.email.embedded-smtp.enabled:true}")
    private boolean enabled;

    @Value("${cloudpool.email.embedded-smtp.port:2525}")
    private int port;

    private SMTPServer smtpServer;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Embedded SMTP server is disabled.");
            return;
        }

        log.info("Starting Embedded SMTP server on port {}...", port);

        try {
            smtpServer = SMTPServer.port(port)
                    .messageHandler((context, from, recipient, data) -> {
                        try {
                            log.info("Incoming email detected from: {} to: {}", from, recipient);
                            
                            Properties props = new Properties();
                            Session session = Session.getDefaultInstance(props, null);
                            
                            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data)) {
                                MimeMessage mimeMessage = new MimeMessage(session, bis);
                                
                                String subject = mimeMessage.getSubject();
                                String body = "";
                                try {
                                    body = getTextFromMessage(mimeMessage);
                                } catch (Exception ex) {
                                    log.warn("Failed to parse email content, using raw content fallback: {}", ex.getMessage());
                                    body = "[Unparseable content: " + ex.getMessage() + "]";
                                }

                                ReceivedEmail email = ReceivedEmail.builder()
                                        .fromAddress(from)
                                        .toAddress(recipient)
                                        .subject(subject != null ? subject : "(No Subject)")
                                        .body(body != null ? body : "")
                                        .receivedAt(LocalDateTime.now())
                                        .build();

                                receivedEmailRepository.save(email);
                                log.info("Received email successfully persisted. ID: {}", email.getId());
                            }
                        } catch (Exception e) {
                            log.error("Failed to parse and save incoming email: {}", e.getMessage(), e);
                        }
                    })
                    .build();

            smtpServer.start();
            log.info("Embedded SMTP server successfully started on port {}.", port);
        } catch (Exception e) {
            log.error("Failed to start Embedded SMTP server on port {}: {}", port, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (smtpServer != null && smtpServer.isRunning()) {
            log.info("Stopping Embedded SMTP server...");
            smtpServer.stop();
            log.info("Embedded SMTP server stopped.");
        }
    }

    private String getTextFromMessage(Part p) throws Exception {
        if (p.isMimeType("text/*")) {
            Object content = p.getContent();
            return content != null ? content.toString() : "";
        }

        if (p.isMimeType("multipart/alternative")) {
            Multipart mp = (Multipart) p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part part = mp.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    Object content = part.getContent();
                    return content != null ? content.toString() : "";
                } else if (part.isMimeType("text/html")) {
                    Object content = part.getContent();
                    if (content != null) {
                        text = content.toString();
                    }
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getTextFromMessage(mp.getBodyPart(i));
                if (s != null && !s.trim().isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }
}
