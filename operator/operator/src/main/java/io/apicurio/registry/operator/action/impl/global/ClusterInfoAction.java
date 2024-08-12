package io.apicurio.registry.operator.action.impl.global;

import io.apicurio.registry.operator.state.impl.ClusterInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteIngress;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;

@ApplicationScoped
public class ClusterInfoAction {

    private static final Logger log = LoggerFactory.getLogger(ClusterInfoAction.class);

    @Inject
    KubernetesClient kc;

    @Inject
    ClusterInfo clusterInfo;

    @Scheduled(every = "10s")
    public void run() {

        var oc = kc.adapt(OpenShiftClient.class);

        if (clusterInfo.getIsOCP() == null) {
            clusterInfo.setIsOCP(Optional.of(oc.isSupported()));
        }

        if (clusterInfo.getCanonicalHost() == null) {
            clusterInfo.getIsOCP().ifPresent(isOCP -> {
                if (isOCP) {
                    // spotless:off
                    // TODO: This caused an exception during testing:
                    // WARN [null] (vertx-blocked-thread-checker) Thread Thread[vert.x-eventloop-thread-1,5,main] has been blocked for 3497 ms, time limit is 2000 ms: io.vertx.core.VertxException: Thread blocked
                    // Consider avoiding search in all namespaces. In the meantime, the time limit has been increased to 8s by:
                    // quarkus.vertx.max-event-loop-execute-time=8s
                    // spotless:on
                    var routes = oc.routes().inAnyNamespace()
                            .withLabel("app.kubernetes.io/name", "apicurio-registry").list();

                    if (routes.getItems().size() > 0) {
                        var canonicalHosts = new HashSet<String>();
                        for (Route route : routes.getItems()) {
                            for (RouteIngress ingress : route.getStatus().getIngress()) {
                                if (ingress.getRouterCanonicalHostname() != null
                                        && !ingress.getRouterCanonicalHostname().isEmpty()) {
                                    canonicalHosts.add(ingress.getRouterCanonicalHostname());
                                }
                            }
                        }
                        if (canonicalHosts.size() == 0) {
                            log.warn("Could not find any canonical hosts.");
                            clusterInfo.setCanonicalHost(Optional.empty());
                        } else {
                            if (canonicalHosts.size() > 1) {
                                log.warn("There are multiple canonical hosts: {}. Selecting randomly.",
                                        canonicalHosts);
                            }
                            clusterInfo.setCanonicalHost(Optional.of(canonicalHosts.iterator().next()));
                        }
                    }

                } else {
                    clusterInfo.setCanonicalHost(Optional.empty());
                }
            });
        }
    }
}
