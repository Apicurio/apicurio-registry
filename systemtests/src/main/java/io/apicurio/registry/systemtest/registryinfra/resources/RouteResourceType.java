package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteTargetReference;

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
        return Kubernetes.getRoute(namespace, name);
    }

    @Override
    public void create(Route resource) {
        Kubernetes.createRoute(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(Route resource) {
        Kubernetes.createOrReplaceRoute(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(Route resource) throws Exception {
        Kubernetes.deleteRoute(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(Route resource) {
        return Kubernetes.isRouteReady(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
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
                    .withName(Environment.KEYCLOAK_HTTP_SERVICE_NAME)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withPath("/")
                    .withTo(new RouteTargetReference() {{
                        setKind("Service");
                        setName(Environment.KEYCLOAK_HTTP_SERVICE_NAME);
                        setWeight(100);
                    }})
                .endSpec()
                .build();
    }
}
