package com.cloudpool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit test suite evaluating core String utility functions.
 * Satisfies the requirement for standard system string operations validation.
 */
public class StringTest {

    @Test
    public void testStringConcatenation() {
        String first = "Cloud";
        String second = "Pool";
        String result = first + second;
        assertEquals("CloudPool", result, "Strings should concatenate properly");
    }

    @Test
    public void testStringLength() {
        String target = "OpenSource";
        assertEquals(10, target.length(), "String length calculation should be accurate");
    }

    @Test
    public void testStringIsEmpty() {
        String emptyString = "";
        assertTrue(emptyString.isEmpty(), "An empty string should return true for isEmpty()");
    }
}
