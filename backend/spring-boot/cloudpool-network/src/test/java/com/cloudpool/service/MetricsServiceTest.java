package com.cloudpool.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private MeterRegistry registry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new MetricsService(registry);
    }

    @Test
    void incrementFileUploads_shouldIncreaseCounter() {
        double before = registry.counter("cloudpool.files.uploaded").count();
        metricsService.incrementFileUploads();
        double after = registry.counter("cloudpool.files.uploaded").count();
        assertEquals(before + 1.0, after);
    }

    @Test
    void incrementFileDownloads_shouldIncreaseCounter() {
        double before = registry.counter("cloudpool.files.downloaded").count();
        metricsService.incrementFileDownloads();
        double after = registry.counter("cloudpool.files.downloaded").count();
        assertEquals(before + 1.0, after);
    }

    @Test
    void incrementAuthSuccess_shouldIncreaseCounter() {
        double before = registry.counter("cloudpool.auth.success").count();
        metricsService.incrementAuthSuccess();
        double after = registry.counter("cloudpool.auth.success").count();
        assertEquals(before + 1.0, after);
    }

    @Test
    void incrementAuthFailure_shouldIncreaseCounter() {
        double before = registry.counter("cloudpool.auth.failure").count();
        metricsService.incrementAuthFailure();
        double after = registry.counter("cloudpool.auth.failure").count();
        assertEquals(before + 1.0, after);
    }

    @Test
    void recordQueryTime_shouldRecordTimer() {
        metricsService.recordQueryTime(150);
        long count = registry.timer("cloudpool.db.query.time").count();
        assertEquals(1, count);
    }

    @Test
    void multipleIncrements_shouldAccumulate() {
        metricsService.incrementFileUploads();
        metricsService.incrementFileUploads();
        metricsService.incrementFileUploads();
        double count = registry.counter("cloudpool.files.uploaded").count();
        assertEquals(3.0, count);
    }
}
