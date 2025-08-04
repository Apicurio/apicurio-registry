package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PortForwardManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManager.class);

    private final KubernetesClient k8sClient;

    private int nextLocalPort = 55001;

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
        log.warn("Starting port-forward {}:{}->{}", targetService, targetPort, localPort);
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

    public int startServicePortForward(String targetService, int targetPort) {
        return startServicePortForward(targetService, targetPort, nextLocalPort++);
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
