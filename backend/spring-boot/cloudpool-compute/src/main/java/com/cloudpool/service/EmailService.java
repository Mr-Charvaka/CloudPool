package com.cloudpool.service;

import com.cloudpool.model.OutboxEmail;
import com.cloudpool.model.ReceivedEmail;
import com.cloudpool.repository.OutboxEmailRepository;
import com.cloudpool.repository.ReceivedEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.*;
import javax.naming.directory.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final OutboxEmailRepository outboxEmailRepository;
    private final ReceivedEmailRepository receivedEmailRepository;

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

    @Value("${cloudpool.email.direct-delivery-from:noreply@cloudpool.dev}")
    private String directDeliveryFrom;

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

    /**
     * Sends an email directly to the recipient's mail server by resolving the MX record.
     * No third-party SMTP relay or API key required. Fully self-hosted.
     *
     * NOTE: Emails sent this way may land in spam/junk because the sending domain
     * has no SPF, DKIM, or DMARC records configured.
     */
    @Transactional
    public OutboxEmail sendDirectEmail(String to, String subject, String body) {
        log.info("Preparing DIRECT MX delivery. To: {}, Subject: {}", to, subject);

        OutboxEmail outboxEmail = OutboxEmail.builder()
                .toAddress(to)
                .subject(subject)
                .body(body)
                .sentAt(LocalDateTime.now())
                .status("QUEUED")
                .build();
        outboxEmail = outboxEmailRepository.save(outboxEmail);

        try {
            // Extract recipient domain
            String recipientDomain = to.substring(to.indexOf('@') + 1);
            log.info("Resolving MX records for domain: {}", recipientDomain);

            // DNS MX Lookup
            String mxHost = resolveMxRecord(recipientDomain);
            log.info("Resolved MX host: {} for domain: {}", mxHost, recipientDomain);

            // Build SMTP session for direct delivery (no auth, port 25)
            Properties props = new Properties();
            props.put("mail.smtp.host", mxHost);
            props.put("mail.smtp.port", "25");
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");
            // Set HELO hostname
            props.put("mail.smtp.localhost", "cloudpool.dev");

            Session session = Session.getInstance(props);
            session.setDebug(true);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(directDeliveryFrom, "CloudPool Mail"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body, "UTF-8");
            message.setHeader("X-Mailer", "CloudPool-DirectMX/1.0");
            message.setSentDate(new java.util.Date());

            Transport transport = session.getTransport("smtp");
            transport.connect(mxHost, 25, null, null);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();

            outboxEmail.setStatus("DELIVERED");
            log.info("Email DIRECTLY delivered to {} via MX host {}", to, mxHost);

        } catch (Exception e) {
            log.error("Direct MX delivery failed to {}: {}", to, e.getMessage(), e);
            outboxEmail.setStatus("FAILED");
            outboxEmail.setErrorMessage(e.getMessage());
        }

        return outboxEmailRepository.save(outboxEmail);
    }

    /**
     * Resolves the MX record for a domain using JNDI DNS lookup.
     * Falls back to the domain itself if no MX record is found.
     */
    private String resolveMxRecord(String domain) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

        DirContext ctx = new InitialDirContext(env);
        Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
        javax.naming.directory.Attribute mxAttr = attrs.get("MX");

        if (mxAttr == null || mxAttr.size() == 0) {
            log.warn("No MX record found for {}. Falling back to domain A-record.", domain);
            ctx.close();
            return domain;
        }

        // Parse MX records (format: "priority hostname")
        // Pick the lowest priority (highest preference)
        String bestMx = null;
        int bestPriority = Integer.MAX_VALUE;

        for (int i = 0; i < mxAttr.size(); i++) {
            String mxRecord = mxAttr.get(i).toString();
            String[] parts = mxRecord.split("\\s+");
            if (parts.length >= 2) {
                int priority = Integer.parseInt(parts[0]);
                String host = parts[1];
                // Remove trailing dot if present
                if (host.endsWith(".")) {
                    host = host.substring(0, host.length() - 1);
                }
                log.debug("MX Record: priority={} host={}", priority, host);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    bestMx = host;
                }
            }
        }

        ctx.close();

        if (bestMx == null) {
            throw new RuntimeException("Failed to parse MX records for domain: " + domain);
        }

        return bestMx;
    }

    public List<OutboxEmail> getOutboxEmails() {
        return outboxEmailRepository.findAll();
    }

    @Transactional
    public void clearOutbox() {
        outboxEmailRepository.deleteAll();
    }

    public List<ReceivedEmail> getReceivedEmails() {
        return receivedEmailRepository.findAll();
    }

    @Transactional
    public void clearInbox() {
        receivedEmailRepository.deleteAll();
    }
}
