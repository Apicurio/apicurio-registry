package io.apicurio.registry.resolver.telemetry;

import io.apicurio.registry.resolver.client.RegistryClientFacade;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsageTelemetryReporter implements Closeable {

    private static final Logger logger = Logger.getLogger(UsageTelemetryReporter.class.getName());
    private static final int MAX_BATCH_MULTIPLIER = 2;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final RegistryClientFacade clientFacade;
    private final int bufferSize;
    private final ConcurrentLinkedQueue<UsageTelemetryEvent> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;

    public UsageTelemetryReporter(RegistryClientFacade clientFacade, int bufferSize,
                                  long flushIntervalMs) {
        this.clientFacade = clientFacade;
        this.bufferSize = bufferSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "apicurio-usage-telemetry");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void recordEvent(UsageTelemetryEvent event) {
        buffer.add(event);
        if (buffer.size() >= bufferSize) {
            scheduler.submit(this::flush);
        }
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<UsageTelemetryEvent> batch = new ArrayList<>();
        UsageTelemetryEvent event;
        while ((event = buffer.poll()) != null && batch.size() < bufferSize * MAX_BATCH_MULTIPLIER) {
            batch.add(event);
        }
        if (!batch.isEmpty()) {
            try {
                clientFacade.reportUsageEvents(batch);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to report usage telemetry events", e);
            }
        }
    }

    @Override
    public void close() {
        flush();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
