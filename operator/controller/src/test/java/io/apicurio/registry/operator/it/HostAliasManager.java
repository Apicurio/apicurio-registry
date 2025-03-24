package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.*;
import java.util.Map.Entry;

import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class HostAliasManager {

    private final KubernetesClient client;

    // IP -> {host alias}
    private final Map<String, Set<String>> hostAliasesMap = new HashMap<>();

    public HostAliasManager(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Define a host alias based on ApicurioRegistry3 ingress values.
     */
    public void defineHostAlias(ApicurioRegistry3 source) {
        ofNullable(source.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getIngress)
                .map(IngressSpec::getHost)
                .ifPresent(h -> defineHostAlias(h, source.getMetadata().getName() + "-app-service"));
        ofNullable(source.getSpec())
                .map(ApicurioRegistry3Spec::getUi)
                .map(UiSpec::getIngress)
                .map(IngressSpec::getHost)
                .ifPresent(h -> defineHostAlias(h, source.getMetadata().getName() + "-ui-service"));
    }

    /**
     * Define a host alias to the target service.
     */
    public void defineHostAlias(String host, String serviceName) {
        await().ignoreExceptions().until(() -> client.services().withName(serviceName).get() != null);
        var clusterIP = client.services().withName(serviceName).get().getSpec().getClusterIP();
        assertThat(clusterIP).isNotBlank();
        hostAliasesMap.compute(clusterIP, (_ignored, v) -> {
            if (v == null) {
                v = new HashSet<>();
            }
            v.add(host);
            return v;
        });
    }

    /**
     * Inject host aliases that have been defined into the resource.
     */
    public void inject(PodTemplateSpec target) {
        if (hostAliasesMap.isEmpty()) {
            return;
        }
        var spec = target.getSpec();
        if (spec == null) {
            spec = new PodSpec();
            target.setSpec(spec);
        }
        var hostAliases = spec.getHostAliases();
        if (hostAliases == null) {
            hostAliases = new ArrayList<>();
            spec.setHostAliases(hostAliases);
        }
        for (Entry<String, Set<String>> entry : hostAliasesMap.entrySet()) {
            var hostAlias = hostAliases.stream().filter(ha -> ha.getIp().equals(entry.getKey())).findFirst().orElse(null);
            if (hostAlias == null) {
                hostAlias = new HostAlias(new ArrayList<>(), entry.getKey());
                hostAliases.add(hostAlias);
            }
            hostAlias.getHostnames().addAll(entry.getValue());
        }
    }

    /**
     * Inject host aliases that have been defined into the resource.
     */
    public void inject(ApicurioRegistry3 target) {
        var pts = ofNullable(target.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getPodTemplateSpec).orElse(null);
        if (pts == null) {
            pts = new PodTemplateSpec();
            target.getSpec().withApp().setPodTemplateSpec(pts);
        }
        inject(pts);
        pts = ofNullable(target.getSpec()).map(ApicurioRegistry3Spec::getUi).map(UiSpec::getPodTemplateSpec).orElse(null);
        if (pts == null) {
            pts = new PodTemplateSpec();
            target.getSpec().withUi().setPodTemplateSpec(pts);
        }
        inject(pts);
    }

    public void clear() {
        hostAliasesMap.clear();
    }
}
