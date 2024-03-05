package io.apicurio.deployment.k8s;

import io.fabric8.kubernetes.client.LocalPortForward;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;

public class PortForwardManager {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManager.class);

    private static final Map<PortForwardKey, LocalPortForward> portForwardMap = new HashMap<>();


    /**
     * Remote port is always the container port, even if the Service exposes a different one.
     */
    public static void startPortForward(String serviceName, int remotePort, int localPort) {
        try {
            log.debug("Starting port forward {}@{} <-> {}", remotePort, serviceName, localPort);
            var keyOpt = portForwardMap.keySet().stream().filter(k -> k.localPort == localPort).findFirst();
            if (keyOpt.isPresent()) {
                log.warn("Port forwarding to local port {} already exists: {}", keyOpt.get().localPort, keyOpt.get());
                log.warn("Closing and restarting.");
                portForwardMap.remove(keyOpt.get()).close();
            }
            var key = new PortForwardKey(serviceName, remotePort, localPort);
            var value = kubernetesClient().services()
                    .inNamespace(TEST_NAMESPACE)
                    .withName(serviceName)
                    .portForward(remotePort, localPort);
            portForwardMap.put(key, value);
        } catch (IOException ex) {
            log.warn("Port forwarding error. Continuing.", ex);
        }
    }


    public static void startPortForward(String serviceName, int port) {
        startPortForward(serviceName, port, port);
    }


    public static void stopPortForward(int port) {
        var keyOpt = portForwardMap.keySet().stream().filter(k -> k.localPort == port).findFirst();
        if (keyOpt.isPresent()) {
            var value = portForwardMap.remove(keyOpt.get());
            try {
                value.close();
            } catch (IOException ex) {
                log.warn("Port forwarding error. Continuing.", ex);
            }
        }
    }


    public static void closePortForwards() {
        portForwardMap.values().forEach(v -> {
            try {
                v.close();
            } catch (IOException ex) {
                log.warn("Port forwarding error. Continuing.", ex);
            }
        });
        portForwardMap.clear();
    }


    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    private static class PortForwardKey {
        private String serviceName;
        private int remotePort;
        private int localPort;
    }
}
