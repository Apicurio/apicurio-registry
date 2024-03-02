package io.apicurio.deployment.manual;

import io.apicurio.deployment.k8s.PodLogWatcher;
import io.apicurio.deployment.k8s.bundle.ManualRegistryBundle;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static io.apicurio.deployment.k8s.PortForwardManager.startPortForward;
import static io.apicurio.deployment.k8s.PortForwardManager.stopPortForward;
import static java.lang.Math.max;

public class ClusterRegistryRunner implements RegistryRunner {

    private static final Logger log = LoggerFactory.getLogger(ClusterRegistryRunner.class);

    @Getter
    private int nodeId;

    @Getter
    private Map<String, Object> report = new ConcurrentHashMap<>();

    private Thread thread;

    private final ManualRegistryBundle bundle;

    private PodLogWatcher logWatcher;

    private String clientURL;


    public ClusterRegistryRunner() {
        this.bundle = new ManualRegistryBundle();
    }


    public synchronized void start(int nodeId, Instant startingLine, String image, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter) {
        if (isStarted()) {
            throw new IllegalStateException("Already started.");
        }

        this.nodeId = nodeId;
        report = new HashMap<>();

        var javaOptions = new ArrayList<String>();
        javaOptions.add(String.format("-Dregistry.kafkasql.bootstrap.servers=%s", bootstrapServers));
        javaOptions.addAll(args);

        thread = new Thread(() -> {
            try {
                Thread.sleep(max(0, Duration.between(Instant.now(), startingLine).toMillis()));
                bundle.deploy(nodeId, image, javaOptions, 8780 + nodeId);
                bundle.waitUtilDeployed();
                logWatcher = new PodLogWatcher(bundle.getPodRef(), "node " + nodeId, line -> reporter.accept(line, this));
                logWatcher.start();
            } catch (Exception ex) {
                log.error("Error:", ex);
            }
        });
        thread.start();
    }


    @Override
    public String getClientURL() {
        if (!isStarted()) {
            throw new IllegalStateException("Not started.");
        }
        if (clientURL == null) {
            startPortForward("apicurio-registry-node-" + nodeId + "-service", 8080, 8780 + nodeId);
            clientURL = String.format("http://localhost:" + (8780 + nodeId));
        }
        return clientURL;
    }


    public synchronized boolean isStarted() {
        return bundle.isDeployed() &&
                (logWatcher != null && logWatcher.isStarted());
    }


    public synchronized boolean isStopped() {
        return bundle.isNotDeployed() &&
                (logWatcher == null || !logWatcher.isStarted());
    }


    @SneakyThrows
    @Override
    public synchronized void stop() {
        if (clientURL != null) {
            stopPortForward(8780 + nodeId);
            clientURL = null;
        }
        if (thread != null) {
            thread.join(60 * 1000);
        }
        if (logWatcher != null) {
            logWatcher.stop();
        }
        bundle.delete();
    }


    @SneakyThrows
    @Override
    public synchronized void stopAndWait() {
        if (clientURL != null) {
            stopPortForward(8780 + nodeId);
            clientURL = null;
        }
        if (thread != null) {
            thread.join(60 * 1000);
        }
        if (logWatcher != null) {
            logWatcher.stopAndWait();
        }
        bundle.deleteAndWait();
    }
}
