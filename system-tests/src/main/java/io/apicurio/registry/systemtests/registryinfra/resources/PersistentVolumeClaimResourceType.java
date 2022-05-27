package io.apicurio.registry.systemtests.registryinfra.resources;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;

import java.time.Duration;
import java.util.HashMap;

public class PersistentVolumeClaimResourceType implements ResourceType<PersistentVolumeClaim> {
    @Override
    public Duration getTimeout() {
        return Duration.ofMinutes(1);
    }

    @Override
    public String getKind() {
        return ResourceKind.PERSISTENT_VOLUME_CLAIM;
    }

    @Override
    public PersistentVolumeClaim get(String namespace, String name) {
        return Kubernetes.getPersistentVolumeClaim(namespace, name);
    }

    @Override
    public void create(PersistentVolumeClaim resource) {
        Kubernetes.createPersistentVolumeClaim(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void createOrReplace(PersistentVolumeClaim resource) {
        Kubernetes.createOrReplacePersistentVolumeClaim(resource.getMetadata().getNamespace(), resource);
    }

    @Override
    public void delete(PersistentVolumeClaim resource) {
        Kubernetes.deletePersistentVolumeClaim(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
    }

    @Override
    public boolean isReady(PersistentVolumeClaim resource) {
        PersistentVolumeClaim persistentVolumeClaim = get(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName()
        );

        if (persistentVolumeClaim == null) {
            return false;
        }

        return persistentVolumeClaim.getStatus().getPhase().equals("Bound");
    }

    @Override
    public boolean doesNotExist(PersistentVolumeClaim resource) {
        if (resource == null) {
            return true;
        }

        return get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null;
    }

    @Override
    public void refreshResource(PersistentVolumeClaim existing, PersistentVolumeClaim newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static PersistentVolumeClaim getDefaultPostgresql(String name, String namespace, String quantity) {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withLabels(new HashMap<>() {{
                    put("app", name);
                }})
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity(quantity))
                .endResources()
                .endSpec()
                .build();
    }

    public static PersistentVolumeClaim getDefaultPostgresql(String name, String namespace) {
        return getDefaultPostgresql(name, namespace, "300Mi");
    }

    public static PersistentVolumeClaim getDefaultPostgresql() {
        return getDefaultPostgresql("postgresql", "postgresql");
    }
}
