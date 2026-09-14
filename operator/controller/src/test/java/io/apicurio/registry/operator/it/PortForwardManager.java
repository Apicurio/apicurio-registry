package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PortForwardManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManager.class);

    private static final int FIRST_LOCAL_PORT = 55001;

    // How many ports to try before giving up, when the OS says the chosen one is taken.
    private static final int BIND_ATTEMPTS = 20;

    private final KubernetesClient k8sClient;

    // Static, and deliberately so. A PortForwardManager is created per test class (see
    // ITBase) but Surefire reuses one JVM across classes, so an instance counter restarted
    // every class at the same base port and handed out ports whose sockets the *previous*
    // class had only just closed - still in TIME_WAIT, and so unbindable. That surfaced as
    // "IllegalStateException: Unable to port forward / Caused by: BindException: Address
    // already in use", typically in whichever class happened to run late in a job.
    // Keeping the counter per-JVM means a port is never handed out twice in one JVM.
    private static final AtomicInteger nextLocalPort = new AtomicInteger(FIRST_LOCAL_PORT);

    private final Map<Integer, LocalPortForward> portForwardMap = new HashMap<>();

    public PortForwardManager(String namespace) {
        // We need higher timeouts for remote debugging. Since the default client is shared with the local operator,
        // we will rather create another client instance than change the defaults.
        this.k8sClient = new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder(Config.autoConfigure(null))
                        .withNamespace(namespace)
                        .withRequestTimeout(100 * 1000)
                        .withConnectionTimeout(100 * 1000)
                        .build())
                .build();
    }

    public synchronized int startPodPortForward(String targetPod, int targetPort, int localPort) {
        check(localPort);
        var pf = k8sClient.pods().withName(targetPod).portForward(targetPort, localPort);
        portForwardMap.put(localPort, pf);
        return localPort;
    }

    public synchronized int startServicePortForward(String targetService, int targetPort, int localPort) {
        log.debug("Starting port-forward {}:{}->{}", targetService, targetPort, localPort);
        check(localPort);
        var pf = k8sClient.services().withName(targetService).portForward(targetPort, localPort);
        portForwardMap.put(localPort, pf);
        return localPort;
    }

    private void check(int localPort) {
        if (portForwardMap.containsKey(localPort)) {
            throw new IllegalArgumentException("Port " + localPort + " is already in use.");
        }
    }

    /**
     * Starts a port-forward on an automatically chosen free local port.
     * <p>
     * The counter alone is not enough: it only guarantees this JVM does not reuse a port, not
     * that the port is free on the machine - anything else on the runner (or a lingering
     * socket) can hold it. Since the only reliable test of "can I bind this" is to bind it,
     * an in-use port is skipped and the next one tried, rather than failing the test on what
     * is a local environment condition and not a product defect.
     */
    public int startServicePortForward(String targetService, int targetPort) {
        IllegalStateException lastFailure = null;
        for (int attempt = 0; attempt < BIND_ATTEMPTS; attempt++) {
            int localPort = nextLocalPort.getAndIncrement();
            try {
                return startServicePortForward(targetService, targetPort, localPort);
            } catch (IllegalStateException ex) {
                if (!isAddressInUse(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                log.info("Local port {} is already in use, trying the next one.", localPort);
            }
        }
        throw new IllegalStateException(
                "Could not find a free local port for a port-forward to " + targetService + ":"
                        + targetPort + " after " + BIND_ATTEMPTS + " attempts.",
                lastFailure);
    }

    /**
     * The client wraps the real cause, so unwrap rather than matching on message text.
     */
    private static boolean isAddressInUse(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    public synchronized LocalPortForward getPortForward(int localPort) {
        return portForwardMap.get(localPort);
    }

    public synchronized void stop(int localPort) {
        if (portForwardMap.containsKey(localPort)) {
            var pf = portForwardMap.get(localPort);
            try {
                pf.close();
            } catch (Exception ex) {
                log.error("Could not close port-forward to {}.", pf.getLocalPort(), ex);
            }
            portForwardMap.remove(localPort);
        } else {
            log.warn("Port-forward to {} does not exist.", localPort);
        }
    }

    public synchronized void stopAll() {
        portForwardMap.values().forEach(pf -> {
            try {
                pf.close();
            } catch (Exception ex) {
                log.error("Could not close port-forward to {}.", pf.getLocalPort(), ex);
            }
        });
        portForwardMap.clear();
    }

    @Override
    public void close() {
        stopAll();
        try {
            k8sClient.close();
        } catch (Exception ex) {
            log.error("Could not close client.", ex);
        }
    }
}
