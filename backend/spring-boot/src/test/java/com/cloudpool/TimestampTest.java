package com.cloudpool;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite verifying standardized timestamp formatting layouts.
 * Ensures data serialization consistency across backend logs and API payloads.
 */
public class TimestampTest {

    @Test
    public void testISO8601StandardFormat() {
        LocalDateTime localDateTime = LocalDateTime.of(2026, 6, 3, 12, 0, 0);
        String expectedFormat = "2026-06-03T12:00:00";
        
        assertEquals(expectedFormat, localDateTime.toString(), "Timestamp should follow standard ISO-8601 format string");
    }

    @Test
    public void testCustomPatternFormatting() {
        LocalDateTime localDateTime = LocalDateTime.of(2026, 6, 3, 15, 30, 45);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        String formattedDate = localDateTime.format(formatter);
        assertEquals("2026-06-03 15:30:45", formattedDate, "Custom timestamp configuration pattern must match explicitly");
    }
}
