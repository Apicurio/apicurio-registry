package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.Utils.isBlank;

/**
 * This is a utility wrapper around {@link SecretKeyRef}, that helps with using the Secret reference in the
 * target Deployment. Usually, a secret value is either provided in an env. variable or accessed as a file.
 * This class helps with both use cases.
 */
public class SecretKeyRefTool {

    private SecretKeyRef secretKeyRef;
    private Integer defaultMode;

    /**
     * Create a utility wrapper around a SecretKeyRef.
     *
     * @param secretKeyRef may be null, but in that case any method other than @{link
     *            SecretKeyRefTool#isValid} will throw an exception.
     * @param defaultKey MUST NOT be null or blank.
     */
    public SecretKeyRefTool(SecretKeyRef secretKeyRef, String defaultKey) {
        if (isBlank(defaultKey)) {
            throw new IllegalArgumentException("defaultKey must not be blank.");
        }
        // Make a copy, so we don't affect the secret ref in the spec.
        if (secretKeyRef != null) {
            this.secretKeyRef = SecretKeyRef.builder().name(secretKeyRef.getName())
                    .key(secretKeyRef.getKey() != null ? secretKeyRef.getKey() : defaultKey).build();
        }
    }

    /**
     * Set file permissions for the mounted Secret volume (e.g., 0400 for SSH private keys).
     * If not set, K8s uses the default (0644).
     */
    public SecretKeyRefTool withDefaultMode(int mode) {
        this.defaultMode = mode;
        return this;
    }

    public boolean isValid() {
        return secretKeyRef != null && !isBlank(secretKeyRef.getName());
    }

    private void requireValid() {
        if (!isValid()) {
            throw new IllegalArgumentException("secretKeyRef " + secretKeyRef + " is not valid.");
        }
    }

    private String getSecretVolumeName() {
        requireValid();
        return secretKeyRef.getName() + "-volume";
    }

    private String getSecretVolumeMountPath() {
        return "/etc/" + getSecretVolumeName();
    }

    /**
     * @return a path to a file mounted within the container that contains the value from the
     *         {@link SecretKeyRef}
     */
    public String getSecretVolumeKeyPath() {
        return getSecretVolumeMountPath() + "/" + secretKeyRef.getKey();
    }

    /**
     * Mount the Secret as a volume and configure its mount inside the container.
     * <p>
     * Use this method in case the {@link SecretKeyRef} references a file to be mounted into the pod. You can
     * then use {@link #getSecretVolumeKeyPath()} to get the path to the file within the pod.
     */
    public void applySecretVolume(Deployment deployment, String containerName) {
        requireValid();
        addSecretVolume(deployment, secretKeyRef.getName(), getSecretVolumeName(), defaultMode);
        addSecretVolumeMount(deployment, containerName, getSecretVolumeName(), getSecretVolumeMountPath());
    }

    /**
     * Add an env. variable that references a value from the Secret.
     * <p>
     * Use this method in case the {@link SecretKeyRef} references data to be provided as an env. variable.
     */
    public void applySecretEnvVar(Map<String, EnvVar> env, String envVarName) {
        requireValid();
        // @formatter:off
        addEnvVar(env, new EnvVarBuilder()
                .withName(envVarName)
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(secretKeyRef.getName())
                        .withKey(secretKeyRef.getKey())
                    .endSecretKeyRef()
                .endValueFrom()
                .build()
        );
        // @formatter:on
    }

    private static void addSecretVolume(Deployment deployment, String secretName, String volumeName,
            Integer defaultMode) {
        // Skip if the volume already exists, so we don't have to add it multiple times.
        // This assumes there is a bijection between the secret names and volume names.
        if (deployment.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> v.getName().equals(volumeName)).findAny().isEmpty()) {
            // @formatter:off
            var volumeBuilder = new VolumeBuilder()
                    .withName(volumeName)
                    .withNewSecret()
                    .withSecretName(secretName);
            if (defaultMode != null) {
                volumeBuilder = volumeBuilder.withDefaultMode(defaultMode);
            }
            deployment.getSpec().getTemplate().getSpec().getVolumes().add(
                    volumeBuilder.endSecret().build()
            );
            // @formatter:on
        }
    }

    private static void addSecretVolumeMount(Deployment deployment, String containerName, String volumeName,
            String mountPath) {
        var c = getContainerFromDeployment(deployment, containerName);
        // Skip if the volume mount already exists, so we don't have to add it multiple times.
        // This assumes there is a bijection between the secret names and volume names.
        if (c.getVolumeMounts().stream().filter(v -> v.getName().equals(volumeName)).findAny().isEmpty()) {
            // @formatter:off
            c.getVolumeMounts().add(
                    new VolumeMountBuilder()
                            .withName(volumeName)
                            .withReadOnly(true)
                            .withMountPath(mountPath)
                            .build()
            );
            // @formatter:on
        }
    }
}
