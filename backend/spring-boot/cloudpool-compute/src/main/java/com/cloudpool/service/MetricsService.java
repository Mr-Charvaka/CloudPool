package com.cloudpool.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MetricsService {

    private final Counter fileUploadsCounter;
    private final Counter fileDownloadsCounter;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;
    private final Timer dbQueryTimer;

    public MetricsService(MeterRegistry registry) {
        this.fileUploadsCounter = Counter.builder("cloudpool.files.uploaded")
                .description("Total number of files uploaded to CloudPool")
                .register(registry);

        this.fileDownloadsCounter = Counter.builder("cloudpool.files.downloaded")
                .description("Total number of files downloaded from CloudPool")
                .register(registry);

        this.authSuccessCounter = Counter.builder("cloudpool.auth.success")
                .description("Total number of successful developer logins")
                .register(registry);

        this.authFailureCounter = Counter.builder("cloudpool.auth.failure")
                .description("Total number of failed developer logins")
                .register(registry);

        this.dbQueryTimer = Timer.builder("cloudpool.db.query.time")
                .description("Execution time of dynamic database console queries")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    public void incrementFileUploads() {
        fileUploadsCounter.increment();
    }

    public void incrementFileDownloads() {
        fileDownloadsCounter.increment();
    }

    public void incrementAuthSuccess() {
        authSuccessCounter.increment();
    }

    public void incrementAuthFailure() {
        authFailureCounter.increment();
    }

    public void recordQueryTime(long durationMs) {
        dbQueryTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
