package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.DeploymentResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.PersistentVolumeClaimResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ServiceResourceType;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DatabaseUtils {
    public static void deployDefaultPostgresqlDatabase(ExtensionContext testContext) {
        ResourceManager resourceManager = ResourceManager.getInstance();

        PersistentVolumeClaim persistentVolumeClaim = PersistentVolumeClaimResourceType.getDefaultPostgresql();
        Deployment deployment = DeploymentResourceType.getDefaultPostgresql();
        Service service = ServiceResourceType.getDefaultPostgresql();

        try {
            resourceManager.createResource(testContext, false, persistentVolumeClaim);
            resourceManager.createResource(testContext, true, deployment);
            resourceManager.createResource(testContext, false, service);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
