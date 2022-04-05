package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.*;

import java.util.HashMap;

public class ServiceResourceType implements ResourceType<Service> {
    @Override
    public String getKind() {
        return ResourceKind.SERVICE;
    }

    @Override
    public Service get(String namespace, String name) {
        return Kubernetes.getClient().services().inNamespace(namespace).withName(name).get();
    }

    @Override
    public void create(Service resource) {
        Kubernetes.getClient().services().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(Service resource) {
        Kubernetes.getClient().services().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(Service resource) throws Exception {
        Kubernetes.getClient().services().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
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

        return Kubernetes.getClient().pods().inNamespace(service.getMetadata().getNamespace()).withLabels(service.getSpec().getSelector()).list().getItems().size() > 0;
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
                    .withLabels(new HashMap<String, String>() {{
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
                    .withSelector(new HashMap<String, String>() {{
                        put("app", name);
                    }})
                    .withType("ClusterIP")
                .endSpec()
                .build();
    }

    public static Service getDefaultPostgresql() {
        return getDefaultPostgresql("postgresql", "postgresql");
    }
}
