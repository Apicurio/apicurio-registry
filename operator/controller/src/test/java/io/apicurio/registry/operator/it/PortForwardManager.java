package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PortForwardManager {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManager.class);

    private final KubernetesClient k8sClient;

    private final String namespace;

    private int nextLocalPort = 55001;

    private final Map<Integer, LocalPortForward> portForwardMap = new HashMap<>();

    public PortForwardManager(KubernetesClient k8sClient, String namespace) {
        this.k8sClient = k8sClient;
        this.namespace = namespace;
    }

    public int startPortForward(String targetService, int targetPort) {
        int localPort = nextLocalPort++;
        var pf = k8sClient.services().inNamespace(namespace).withName(targetService).portForward(targetPort,
                localPort);
        portForwardMap.put(localPort, pf);
        return localPort;
    }

    public void stop() {
        portForwardMap.values().forEach(pf -> {
            try {
                pf.close();
            } catch (IOException ex) {
                log.error("Could not close port forward " + pf, ex);
            }
        });
    }
}
