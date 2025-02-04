package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class PodLogManager {

    private static final Logger log = LoggerFactory.getLogger(PodLogManager.class);

    private final KubernetesClient k8sClient;

    private final Map<ResourceID, AtomicBoolean> activePodLogMap = new ConcurrentHashMap<>();

    public PodLogManager(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
    }

    private String getNamespace(ResourceID id) {
        return id.getNamespace().orElse("default");
    }

    public void startPodLog(ResourceID podID) {
        k8sClient.pods().inNamespace(getNamespace(podID)).withName(podID.getName()).waitUntilReady(60,
                SECONDS);
        new Thread(() -> {
            StringBuilder chunk = new StringBuilder();
            try (
                    LogWatch logWatch = k8sClient.pods().inNamespace(getNamespace(podID)).withName(podID.getName()).watchLog();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()))
            ) {
                AtomicBoolean stop = new AtomicBoolean(false);
                log.debug("START LOG of pod {}/{}", getNamespace(podID), podID.getName());
                activePodLogMap.put(podID, stop);
                var lastWriteAt = Instant.now();
                while (!stop.get()) {
                    var line = reader.readLine();
                    if (line != null) {
                        chunk.append(getNamespace(podID)).append("/").append(podID.getName()).append(" >>> ")
                                .append(line).append("\n");
                        if (lastWriteAt.plus(Duration.ofSeconds(5)).isBefore(Instant.now())) {
                            log.debug("LOG of pod {}/{}:\n{}", getNamespace(podID), podID.getName(), chunk);
                            chunk.setLength(0);
                            lastWriteAt = Instant.now();
                        }
                    } else {
                        stop.set(true);
                    }
                }
            } catch (Exception ex) {
                log.error("Error while reading logs of pod {}/{}", getNamespace(podID), podID.getName(), ex);
            } finally {
                if (chunk.length() > 0) {
                    log.debug("LOG of pod {}/{}:\n{}", getNamespace(podID), podID.getName(), chunk);
                }
                log.debug("END LOG of pod {}/{}", getNamespace(podID), podID.getName());
                activePodLogMap.remove(podID);
            }
        }).start();
    }

    public void stopAndWait() {
        activePodLogMap.values().forEach(stop -> stop.set(true));
        await().until(activePodLogMap::isEmpty);
    }
}
