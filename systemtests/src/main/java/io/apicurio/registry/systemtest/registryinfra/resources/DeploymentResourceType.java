package io.apicurio.registry.systemtest.registryinfra.resources;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

public class DeploymentResourceType implements ResourceType<Deployment> {
    @Override
    public String getKind() {
        return ResourceKind.DEPLOYMENT;
    }

    @Override
    public Deployment get(String namespace, String name) {
        return Kubernetes.getClient().apps().deployments().inNamespace(namespace).withName(name).get();
    }

    @Override
    public void create(Deployment resource) {
        Kubernetes.getClient().apps().deployments().inNamespace(resource.getMetadata().getNamespace()).create(resource);
    }

    @Override
    public void createOrReplace(Deployment resource) {
        Kubernetes.getClient().apps().deployments().inNamespace(resource.getMetadata().getNamespace()).createOrReplace(resource);
    }

    @Override
    public void delete(Deployment resource) throws Exception {
        Kubernetes.getClient().apps().deployments().inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public boolean isReady(Deployment resource) {
        Deployment deployment = get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (deployment == null) {
            return false;
        }

        DeploymentSpec deploymentSpec = deployment.getSpec();
        DeploymentStatus deploymentStatus = deployment.getStatus();

        if (deploymentStatus == null || deploymentStatus.getReplicas() == null || deploymentStatus.getAvailableReplicas() == null) {
            return false;
        }

        if (deploymentSpec == null || deploymentSpec.getReplicas() == null) {
            return false;
        }

        return deploymentSpec.getReplicas().intValue() == deploymentStatus.getReplicas() && deploymentSpec.getReplicas() <= deploymentStatus.getAvailableReplicas();
    }

    @Override
    public void refreshResource(Deployment existing, Deployment newResource) {
        existing.setMetadata(newResource.getMetadata());
        existing.setSpec(newResource.getSpec());
        existing.setStatus(newResource.getStatus());
    }

    /** Get default instances **/

    public static Deployment getDefaultPostgresql(String name, String namespace) {
        return new DeploymentBuilder()
                .withNewMetadata()
                    .addToLabels("app", name)
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                        .addToMatchLabels("app", name)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", name)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withEnv(
                                    new EnvVar("POSTGRESQL_ADMIN_PASSWORD", "adminpassword", null),
                                    new EnvVar("POSTGRESQL_DATABASE", "postgresdb", null),
                                    new EnvVar("POSTGRESQL_USER", "postgresuser", null),
                                    new EnvVar("POSTGRESQL_PASSWORD", "postgrespassword", null)
                                )
                                .withImage("quay.io/centos7/postgresql-12-centos7:latest")
                                .withImagePullPolicy("Always")
                                .withName(name)
                                .addNewPort()
                                    .withContainerPort(5432)
                                    .withName("postgresql")
                                    .withProtocol("TCP")
                                .endPort()
                                .withNewReadinessProbe()
                                    .withNewTcpSocket()
                                        .withNewPort(5432)
                                    .endTcpSocket()
                                .endReadinessProbe()
                                .withNewLivenessProbe()
                                    .withNewTcpSocket()
                                        .withNewPort(5432)
                                    .endTcpSocket()
                                .endLivenessProbe()
                                .withVolumeMounts(new VolumeMount() {{
                                    setMountPath("/var/lib/pgsql/data");
                                    setName(name);
                                }})
                            .endContainer()
                            .withVolumes(new Volume() {{
                                setName(name);
                                setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource() {{
                                    setClaimName(name);
                                }});
                            }})
                            .withRestartPolicy("Always")
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    public static Deployment getDefaultPostgresql() {
        return getDefaultPostgresql("postgresql", "postgresql");
    }
}
