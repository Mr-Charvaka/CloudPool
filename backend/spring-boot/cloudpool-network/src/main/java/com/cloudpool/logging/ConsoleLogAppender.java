package com.cloudpool.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.cloudpool.dto.ConsoleLogEvent;
import com.cloudpool.service.ConsoleLogService;
import java.time.Instant;
import java.util.List;

/**
 * Logback appender that forwards application log events to the developer console stream.
 */
public class ConsoleLogAppender extends AppenderBase<ILoggingEvent> {

    static final int MAX_MESSAGE_LENGTH = 4000;

    /** Loggers involved in delivering the stream itself; forwarding them would feed back into it. */
    private static final List<String> EXCLUDED_LOGGER_PREFIXES = List.of(
            "org.springframework.messaging",
            "org.springframework.web.socket",
            "com.cloudpool.service.ConsoleLogService",
            "com.cloudpool.logging");

    private static final ThreadLocal<Boolean> APPENDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final ConsoleLogService service;
    private final Level minLevel;

    public ConsoleLogAppender(ConsoleLogService service, Level minLevel) {
        this.service = service;
        this.minLevel = minLevel;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(minLevel) || isExcluded(event.getLoggerName())) {
            return;
        }
        if (APPENDING.get()) {
            return;
        }
        APPENDING.set(Boolean.TRUE);
        try {
            service.offer(new ConsoleLogEvent(
                    Instant.ofEpochMilli(event.getTimeStamp()),
                    event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getThreadName(),
                    buildMessage(event)));
        } finally {
            APPENDING.set(Boolean.FALSE);
        }
    }

    private static boolean isExcluded(String loggerName) {
        return loggerName != null && EXCLUDED_LOGGER_PREFIXES.stream().anyMatch(loggerName::startsWith);
    }

    private static String buildMessage(ILoggingEvent event) {
        StringBuilder message = new StringBuilder(event.getFormattedMessage());
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            message.append('\n').append(throwable.getClassName());
            if (throwable.getMessage() != null) {
                message.append(": ").append(throwable.getMessage());
            }
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message.setLength(MAX_MESSAGE_LENGTH);
            message.append("... [truncated]");
        }
        return message.toString();
    }
}
