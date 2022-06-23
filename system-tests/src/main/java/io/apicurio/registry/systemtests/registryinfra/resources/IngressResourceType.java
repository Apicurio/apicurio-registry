package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBackend;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackend;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;

public class IngressResourceType implements ResourceType<Ingress> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.INGRESS;
    }

    @Override
    public Ingress get(String namespace, String name) {
        return getOperation()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static MixedOperation<Ingress, KubernetesResourceList<Ingress>, Resource<Ingress>> getOperation() {
        return Kubernetes.getResources(Ingress.class);
    }

    @Override
    public void create(Ingress resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(resource);
    }

    @Override
    public void createOrReplace(Ingress resource) {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(resource);
    }

    @Override
    public void delete(Ingress resource) throws Exception {
        getOperation()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .delete();
    }

    @Override
    public boolean isReady(Ingress resource) {
        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) != null;
    }

    @Override
    public boolean doesNotExist(Ingress resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(Ingress existing, Ingress newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Ingress getDefaultSelenium(String name, String namespace)  {
        return new IngressBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("app", name))
                .endMetadata()
                .withNewSpec()
                .withRules(new IngressRule() {{
                    setHost(name + ".127.0.0.1.nip.io");
                    setHttp(new HTTPIngressRuleValue() {{
                        setPaths(new ArrayList<>() {{
                            add(new HTTPIngressPath() {{
                                setPath("/");
                                setPathType("Prefix");
                                setBackend(new IngressBackend() {{
                                    setService(new IngressServiceBackend() {{
                                        setName(name);
                                        setPort(new ServiceBackendPort() {{
                                            setNumber(4444);
                                        }});
                                    }});
                                }});
                            }});
                        }});
                    }});
                }})
                .endSpec()
                .build();
    }

    public static Ingress getDefaultSelenium() {
        return getDefaultSelenium("selenium-chrome", "selenium");
    }
}
