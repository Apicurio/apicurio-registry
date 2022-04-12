package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;

import java.util.HashMap;

public class PersistentVolumeClaimResourceType implements ResourceType<PersistentVolumeClaim> {
    @Override
    public String getKind() {
        return ResourceKind.PERSISTENT_VOLUME_CLAIM;
    }

    @Override
    public PersistentVolumeClaim get(String namespace, String name) {
        return Kubernetes.getClient().persistentVolumeClaims().inNamespace(namespace).withName(name).get();
    }

    @Override
    public void create(PersistentVolumeClaim resource) {
        Kubernetes.getClient().persistentVolumeClaims().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(PersistentVolumeClaim resource) {
        Kubernetes.getClient().persistentVolumeClaims().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(PersistentVolumeClaim resource) {
        Kubernetes.getClient().persistentVolumeClaims().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(PersistentVolumeClaim resource) {
        PersistentVolumeClaim persistentVolumeClaim = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (persistentVolumeClaim == null) {
            return false;
        }

        return persistentVolumeClaim.getStatus().getPhase().equals("Bound");
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
