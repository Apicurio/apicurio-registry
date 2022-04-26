package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteTargetReference;
import io.fabric8.openshift.client.OpenShiftClient;

import java.time.Duration;

public class RouteResourceType implements ResourceType<Route> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(3);
    }

    @Override
    public String getKind() {
        return ResourceKind.ROUTE;
    }

    @Override
    public Route get(String namespace, String name) {
        return ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(namespace).withName(name).get();
    }

    @Override
    public void create(Route resource) {
        ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(Route resource) {
        ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(Route resource) throws Exception {
        ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(Route resource) {
        return ((OpenShiftClient) Kubernetes.getClient()).routes().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).get().getStatus().getIngress().size() > 0;
    }

    @Override
    public void refreshResource(Route existing, Route newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Route getDefaultKeycloak(String namespace) {
        return new RouteBuilder()
                .withNewMetadata()
                    .withName(Constants.KEYCLOAK_HTTP_SERVICE_NAME)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withPath("/")
                    .withTo(new RouteTargetReference() {{
                        setKind("Service");
                        setName(Constants.KEYCLOAK_HTTP_SERVICE_NAME);
                        setWeight(100);
                    }})
                .endSpec()
                .build();
    }
}
