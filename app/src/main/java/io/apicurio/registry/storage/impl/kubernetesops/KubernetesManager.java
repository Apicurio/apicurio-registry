package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.storage.impl.gitops.ProcessingState;
import io.apicurio.registry.storage.impl.polling.AbstractDataSourceManager;
import io.apicurio.registry.storage.impl.polling.DataFile;
import io.apicurio.registry.storage.impl.polling.PollResult;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class KubernetesManager extends AbstractDataSourceManager {

    @Inject
    Logger log;

    @Inject
    KubernetesOpsConfigProperties config;

    @Inject
    KubernetesClient kubernetesClient;

    private volatile String previousResourceVersion = "";

    private Watch configMapWatch;
    private volatile boolean watchActive = false;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private Runnable refreshCallback;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kubernetesops-watch-reconnect");
        t.setDaemon(true);
        return t;
    });

    @Override
    protected String getRegistryId() {
        return config.getRegistryId();
    }

    @Override
    protected long getCommitTime(Object marker) {
        return Instant.now().getEpochSecond();
    }

    @Override
    public void start() throws Exception {
        log.info("Initializing KubernetesOps manager with registry ID: {}", config.getRegistryId());
        log.info("Watching namespace: {} for ConfigMaps with selector {}",
                config.getEffectiveNamespace(), config.getLabelSelector());
    }

    @Override
    public PollResult poll() throws Exception {
        String namespace = config.getEffectiveNamespace();
        String labelSelector = config.getLabelSelector();

        var configMapList = kubernetesClient.configMaps()
                .inNamespace(namespace)
                .withLabelSelector(labelSelector)
                .list();

        String currentResourceVersion = configMapList.getMetadata().getResourceVersion();

        if (currentResourceVersion.equals(previousResourceVersion)) {
            return PollResult.noChanges(currentResourceVersion);
        }

        log.debug("Detected change in ConfigMaps: resourceVersion {} -> {}",
                previousResourceVersion, currentResourceVersion);

        List<DataFile> files = new ArrayList<>();
        ProcessingState tempState = new ProcessingState(null);

        for (ConfigMap configMap : configMapList.getItems()) {
            Map<String, String> data = configMap.getData();

            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    String dataKey = entry.getKey();
                    String content = entry.getValue();

                    ConfigMapDataFile file = ConfigMapDataFile.create(tempState, dataKey, content);
                    files.add(file);
                }
            }
        }

        log.debug("Found {} data files across {} ConfigMaps",
                files.size(), configMapList.getItems().size());

        if (!tempState.isSuccessful()) {
            for (String error : tempState.getErrors()) {
                log.warn("ConfigMap parse error: {}", error);
            }
        }

        return PollResult.withChanges(currentResourceVersion, files);
    }

    @Override
    public void commitChange(Object marker) {
        if (marker instanceof String) {
            previousResourceVersion = (String) marker;
        }
    }

    @Override
    public Object getPreviousMarker() {
        return previousResourceVersion;
    }

    /**
     * Sets the callback to be invoked when watch events are received.
     */
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    /**
     * Starts watching ConfigMaps for changes using the Kubernetes Watch API.
     */
    public synchronized void startWatch() {
        if (!config.isWatchEnabled()) {
            log.info("Watch disabled, using polling only");
            return;
        }

        if (watchActive) {
            log.debug("Watch already active, skipping start");
            return;
        }

        try {
            String namespace = config.getEffectiveNamespace();
            String labelSelector = config.getLabelSelector();

            log.info("Starting watch for ConfigMaps in namespace {} with selector {}",
                    namespace, labelSelector);

            configMapWatch = kubernetesClient.configMaps()
                    .inNamespace(namespace)
                    .withLabelSelector(labelSelector)
                    .watch(new Watcher<ConfigMap>() {
                        @Override
                        public void eventReceived(Action action, ConfigMap configMap) {
                            log.debug("ConfigMap event: {} on {}", action,
                                    configMap.getMetadata().getName());

                            if (action == Action.ADDED || action == Action.MODIFIED ||
                                    action == Action.DELETED) {
                                if (refreshCallback != null) {
                                    refreshCallback.run();
                                }
                            }
                        }

                        @Override
                        public void onClose(WatcherException e) {
                            synchronized (KubernetesManager.this) {
                                watchActive = false;
                            }
                            if (e != null) {
                                log.warn("Watch closed with error: {}", e.getMessage());
                                scheduleReconnect();
                            } else {
                                log.info("Watch closed normally");
                            }
                        }
                    });

            watchActive = true;
            reconnectAttempts.set(0);
            log.info("Watch started successfully for ConfigMaps in namespace {}", namespace);

        } catch (Exception e) {
            log.error("Failed to start watch: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private static final long MAX_BACKOFF_MS = 300_000; // 5 minutes

    private void scheduleReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();
        long delayMs = calculateBackoff(attempts, config.getWatchReconnectDelay());

        if (delayMs >= MAX_BACKOFF_MS) {
            log.warn("Watch reconnect at max backoff ({}ms), attempt {}. "
                    + "Check Kubernetes API server connectivity.", delayMs, attempts);
        } else {
            log.info("Scheduling watch reconnect in {}ms (attempt {})", delayMs, attempts);
        }

        scheduler.schedule(this::startWatch, delayMs, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoff(int attempts, java.time.Duration baseDelay) {
        long baseMs = baseDelay.toMillis();
        long delayMs = (long) (baseMs * Math.pow(2, Math.min(attempts - 1, 5)));
        return Math.min(delayMs, MAX_BACKOFF_MS);
    }

    /**
     * Stops the active watch if running.
     */
    public synchronized void stopWatch() {
        if (configMapWatch != null) {
            log.info("Stopping ConfigMap watch");
            configMapWatch.close();
            configMapWatch = null;
            watchActive = false;
        }
    }

    /**
     * Returns whether the watch is currently active.
     */
    public boolean isWatchActive() {
        return watchActive;
    }

    @PreDestroy
    void destroy() {
        stopWatch();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
