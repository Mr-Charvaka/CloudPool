package com.cloudpool.dto;

import java.time.Instant;

/**
 * A single backend log line streamed to the developer console.
 */
public record ConsoleLogEvent(
        Instant timestamp,
        String level,
        String logger,
        String thread,
        String message) {
}
