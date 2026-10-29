package com.cloudpool.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.cloudpool.service.ConsoleLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Attaches the {@link ConsoleLogAppender} to the root logger at runtime, so only this service
 * streams its logs and the logback configuration shared by every module stays untouched.
 */
@Component
@Slf4j
public class ConsoleLogAppenderRegistrar {

    static final String APPENDER_NAME = "CONSOLE_STREAM";

    private final ConsoleLogService service;
    private final boolean enabled;
    private final String minLevel;
    private ConsoleLogAppender appender;
    private Logger rootLogger;

    public ConsoleLogAppenderRegistrar(
            ConsoleLogService service,
            @Value("${cloudpool.console.stream.enabled:true}") boolean enabled,
            @Value("${cloudpool.console.stream.min-level:INFO}") String minLevel) {
        this.service = service;
        this.enabled = enabled;
        this.minLevel = minLevel;
    }

    @PostConstruct
    void attach() {
        if (!enabled) {
            return;
        }
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            log.warn("Console log streaming needs Logback; it is disabled for {}", factory.getClass().getName());
            return;
        }
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(APPENDER_NAME); // a previous context in the same JVM may have left one

        appender = new ConsoleLogAppender(service, Level.toLevel(minLevel, Level.INFO));
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @PreDestroy
    void detach() {
        if (appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
            appender = null;
        }
    }
}
