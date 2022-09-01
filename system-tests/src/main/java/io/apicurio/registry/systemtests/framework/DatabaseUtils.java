package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.DeploymentResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistentVolumeClaimResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.ServiceResourceType;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DatabaseUtils {
    public static void deployDefaultPostgresqlDatabase(ExtensionContext testContext) {
        PersistentVolumeClaim persistentVolumeClaim = PersistentVolumeClaimResourceType.getDefaultPostgresql();
        Deployment deployment = DeploymentResourceType.getDefaultPostgresql();
        Service service = ServiceResourceType.getDefaultPostgresql();

        try {
            ResourceManager.getInstance().createResource(false, persistentVolumeClaim);
            ResourceManager.getInstance().createResource(true, deployment);
            ResourceManager.getInstance().createResource(false, service);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deployPostgresqlDatabase(ExtensionContext testContext, String name, String namespace) {
        PersistentVolumeClaim persistentVolumeClaim = PersistentVolumeClaimResourceType.getDefaultPostgresql(
                name,
                namespace
        );
        Deployment deployment = DeploymentResourceType.getDefaultPostgresql(name, namespace);
        Service service = ServiceResourceType.getDefaultPostgresql(name, namespace);

        try {
            ResourceManager.getInstance().createResource(false, persistentVolumeClaim);
            ResourceManager.getInstance().createResource(true, deployment);
            ResourceManager.getInstance().createResource(false, service);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
