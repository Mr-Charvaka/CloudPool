package com.cloudpool.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.cloudpool.dto.ConsoleLogEvent;
import com.cloudpool.service.ConsoleLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsoleLogAppenderTest {

    private LoggerContext context;
    private ConsoleLogService service;
    private ConsoleLogAppender appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        service = mock(ConsoleLogService.class);
        when(service.offer(any())).thenReturn(true);
        appender = new ConsoleLogAppender(service, Level.INFO);
        appender.setContext(context);
        appender.start();

        logger = context.getLogger("com.example.App");
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        context.stop();
    }

    private ConsoleLogEvent captureSingleEvent() {
        ArgumentCaptor<ConsoleLogEvent> captor = ArgumentCaptor.forClass(ConsoleLogEvent.class);
        verify(service).offer(captor.capture());
        return captor.getValue();
    }

    @Test
    void eventsAtOrAboveTheThresholdAreForwarded() {
        logger.info("deployment {} finished", "abc");

        ConsoleLogEvent event = captureSingleEvent();
        assertEquals("INFO", event.level());
        assertEquals("com.example.App", event.logger());
        assertEquals("deployment abc finished", event.message());
    }

    @Test
    void eventsBelowTheThresholdAreIgnored() {
        logger.debug("noisy");
        logger.trace("noisier");

        verify(service, never()).offer(any());
    }

    @Test
    void logsFromTheStreamingInfrastructureAreIgnoredToAvoidFeedbackLoops() {
        for (String name : new String[] {
                "org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler",
                "org.springframework.web.socket.messaging.StompSubProtocolHandler",
                "com.cloudpool.service.ConsoleLogService",
                "com.cloudpool.logging.ConsoleLogAppender"}) {
            Logger infra = context.getLogger(name);
            infra.addAppender(appender);
            infra.info("would loop");
        }

        verify(service, never()).offer(any());
    }

    @Test
    void anAppendTriggeredByAnAppendDoesNotRecurse() {
        when(service.offer(any())).thenAnswer(invocation -> {
            logger.info("logged while publishing");
            return true;
        });

        logger.info("first");

        verify(service, times(1)).offer(any());
    }

    @Test
    void veryLongMessagesAreTruncated() {
        logger.info("x".repeat(10_000));

        ConsoleLogEvent event = captureSingleEvent();
        assertTrue(event.message().length() <= ConsoleLogAppender.MAX_MESSAGE_LENGTH + 32);
        assertTrue(event.message().endsWith("[truncated]"));
    }

    @Test
    void exceptionsAreSummarizedWithoutTheFullStackTrace() {
        logger.error("deployment failed", new IllegalStateException("boom"));

        ConsoleLogEvent event = captureSingleEvent();
        assertTrue(event.message().startsWith("deployment failed"));
        assertTrue(event.message().contains("java.lang.IllegalStateException: boom"));
        assertTrue(!event.message().contains("\tat "), "stack frames must not be streamed");
    }
}
