package com.cloudpool.service;

import com.cloudpool.dto.ConsoleLogEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Hands log events to the STOMP broker from a dedicated thread. Logging threads only enqueue, so a
 * slow or failing broker can never block the application; when the bounded queue is full the event
 * is dropped and counted.
 */
@Service
@Slf4j
public class ConsoleLogService {

    public static final String TOPIC = "/topic/console/system";

    private final SimpMessagingTemplate messagingTemplate;
    private final BlockingQueue<ConsoleLogEvent> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public ConsoleLogService(
            SimpMessagingTemplate messagingTemplate,
            @Value("${cloudpool.console.stream.queue-capacity:1000}") int queueCapacity) {
        this.messagingTemplate = messagingTemplate;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
    }

    @PostConstruct
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::drain, "console-log-streamer");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    /**
     * @return {@code false} when the queue is full and the event was dropped
     */
    public boolean offer(ConsoleLogEvent event) {
        if (queue.offer(event)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    private void drain() {
        while (running) {
            try {
                ConsoleLogEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                if (event != null) {
                    messagingTemplate.convertAndSend(TOPIC, event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // Keep streaming; a bad event or a broker hiccup must not end the stream.
                log.debug("Could not broadcast a console log event: {}", e.getMessage());
            }
        }
    }
}
