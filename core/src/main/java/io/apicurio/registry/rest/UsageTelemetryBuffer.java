package io.apicurio.registry.rest;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.TimeUnit;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UsageTelemetryBuffer {

    private static final int MAX_BUFFER_SIZE = 1000;
    private static final int MAX_DEDUP_MAP_SIZE = 10_000;
    private static final long DEDUP_WINDOW_MS = 60_000;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    UsageTelemetryConfig config;

    private final ConcurrentLinkedQueue<SchemaUsageEventDto> buffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Long> dedupMap = new ConcurrentHashMap<>();

    public void addEvent(SchemaUsageEventDto event) {
        if (dedupMap.size() >= MAX_DEDUP_MAP_SIZE) {
            return;
        }
        String dedupKey = (event.getGlobalId() > 0 ? "g" + event.getGlobalId() : "c" + event.getContentId())
                + ":" + event.getClientId();
        long now = System.currentTimeMillis();
        Long lastSeen = dedupMap.get(dedupKey);
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
            return;
        }
        dedupMap.put(dedupKey, now);
        buffer.add(event);
    }

    @Scheduled(delay = 5, delayUnit = TimeUnit.SECONDS, concurrentExecution = SKIP, every = "{apicurio.usage.flush.every}")
    void flush() {
        if (!config.isEnabled() || buffer.isEmpty()) {
            return;
        }
        List<SchemaUsageEventDto> batch = new ArrayList<>();
        SchemaUsageEventDto event;
        while ((event = buffer.poll()) != null && batch.size() < MAX_BUFFER_SIZE * 2) {
            batch.add(event);
        }
        if (!batch.isEmpty()) {
            try {
                for (SchemaUsageEventDto e : batch) {
                    storage.recordUsageEvent(e);
                }
            } catch (Exception e) {
                log.warn("Failed to flush usage telemetry events", e);
            }
        }

        long cutoff = System.currentTimeMillis() - DEDUP_WINDOW_MS;
        dedupMap.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        if (dedupMap.size() > MAX_DEDUP_MAP_SIZE) {
            dedupMap.clear();
        }
    }
}
