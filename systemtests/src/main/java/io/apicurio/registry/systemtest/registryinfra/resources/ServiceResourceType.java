package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.framework.Environment;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;

import java.time.Duration;
import java.util.HashMap;

public class ServiceResourceType implements ResourceType<Service> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.SERVICE;
    }

    @Override
    public Service get(String namespace, String name) {
        return Kubernetes.getService(namespace, name);
    }

    @Override
    public void create(Service resource) {
        Kubernetes.createService(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(Service resource) {
        Kubernetes.createOrReplaceService(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(Service resource) {
        Kubernetes.deleteService(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(Service resource) {
        Service service = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (service == null) {
            return false;
        }

        if (service.getSpec().getSelector() == null) {
            return true;
        }

        return Kubernetes.isServiceReady(service.getMetadata().getNamespace(), service.getSpec().getSelector());
    }

    @Override
    public void refreshResource(Service existing, Service newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Service getDefaultPostgresql(String name, String namespace) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withLabels(new HashMap<>() {{
                        put("app", name);
                    }})
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withPorts(new ServicePort() {{
                        setName("postgresql");
                        setPort(5432);
                        setProtocol("TCP");
                        setTargetPort(new IntOrString(5432));
                    }})
                    .withSelector(new HashMap<>() {{
                        put("app", name);
                    }})
                    .withType("ClusterIP")
                .endSpec()
                .build();
    }

    public static Service getDefaultPostgresql() {
        return getDefaultPostgresql("postgresql", "postgresql");
    }

    public static Service getDefaultKeycloakHttp(String namespace) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(Environment.KEYCLOAK_HTTP_SERVICE_NAME)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withPorts(new ServicePort() {{
                        setPort(8080);
                        setProtocol("TCP");
                        setTargetPort(new IntOrString(8080));
                    }})
                    .withSelector(new HashMap<>() {{
                        put("app", "keycloak");
                        put("component", "keycloak");
                    }})
                    .withType("ClusterIP")
                .endSpec()
                .build();
    }
}
