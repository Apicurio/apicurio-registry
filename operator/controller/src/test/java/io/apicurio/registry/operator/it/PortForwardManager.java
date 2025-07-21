package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.apicurio.registry.operator.testutils.Utils.RANDOM;
import static io.apicurio.registry.operator.testutils.Utils.withRetries;
import static java.lang.String.format;

public class PortForwardManager {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManager.class);

    private final KubernetesClient k8sClient;

    private final String namespace;

    private int nextLocalPort = 55001;

    private final Map<Integer, PortForwardHandle> portForwardMap = new HashMap<>();

    public PortForwardManager(KubernetesClient k8sClient, String namespace) {
        this.k8sClient = k8sClient;
        this.namespace = namespace;
    }

    public int startPortForward(String targetService, int targetPort) {
        return withRetries(() -> {
            int localPort = nextLocalPort++;
            log.debug("Initiating port forward {} -> {}/{}:{}", localPort, namespace, targetService, targetPort);
            var pf = k8sClient.services().inNamespace(namespace).withName(targetService).portForward(targetPort, localPort);
            portForwardMap.put(localPort, new PortForwardHandle(pf, localPort, namespace, targetService, targetPort));
            return localPort;
        }, () -> {
            nextLocalPort += RANDOM.nextInt(100, 200);
        }, 3);
    }

    public void stop() {
        var copy = new HashSet<>(portForwardMap.values());
        copy.forEach(handle -> {
            try {
                handle.pf().close();
                portForwardMap.remove(handle.localPort());
                log.debug("Closed port forward " + handle);
            } catch (IOException ex) {
                log.error("Could not close port forward " + handle, ex);
            }
        });
    }

    private record PortForwardHandle(LocalPortForward pf, int localPort, String namespace, String targetService,
                                     int targetPort) {

        @Override
        public String toString() {
            return format("%s -> %s/%s:%s", localPort, namespace, targetService, targetPort);
        }
    }
}
