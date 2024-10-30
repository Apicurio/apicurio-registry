package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlTLSSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromDeployment;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KafkaSqlTLS {

    public static final String ENV_KAFKASQL_SECURITY_PROTOCOL = "APICURIO_KAFKA_COMMON_SECURITY_PROTOCOL";

    public static final String ENV_KAFKASQL_SSL_KEYSTORE_TYPE = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_TYPE";
    public static final String ENV_KAFKASQL_SSL_KEYSTORE_LOCATION = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_LOCATION";
    public static final String ENV_KAFKASQL_SSL_KEYSTORE_PASSWORD = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_PASSWORD";

    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_TYPE = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_TYPE";
    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_LOCATION = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_LOCATION";
    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_PASSWORD = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_PASSWORD";

    public static final String KEYSTORE_SECRET_VOLUME_NAME = "registry-kafkasql-tls-keystore";
    public static final String TRUSTSTORE_SECRET_VOLUME_NAME = "registry-kafkasql-tls-truststore";

    /**
     * Plain KafkaSQL must be already configured.
     */
    public static boolean configureKafkaSQLTLS(ApicurioRegistry3 primary, Deployment deployment,
            String containerName, Map<String, EnvVar> env) {

        // spotless:off
        var keystoreSecretName = ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getKeystoreSecretName)
                .filter(x -> !isBlank(x))
                .orElse(null);

        var truststoreSecretName = ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getTruststoreSecretName)
                .filter(x -> !isBlank(x))
                .orElse(null);
        // spotless:on

        if (keystoreSecretName != null && truststoreSecretName != null) {

            addEnvVar(env, ENV_KAFKASQL_SECURITY_PROTOCOL, "SSL");

            // ===== Keystore

            addEnvVar(env, ENV_KAFKASQL_SSL_KEYSTORE_TYPE, "PKCS12");
            addEnvVar(env, ENV_KAFKASQL_SSL_KEYSTORE_LOCATION,
                    "/etc/" + KEYSTORE_SECRET_VOLUME_NAME + "/user.p12");
            // spotless:off
            // @formatter:off
            addEnvVar(env, new EnvVarBuilder()
                    .withName(ENV_KAFKASQL_SSL_KEYSTORE_PASSWORD)
                    .withNewValueFrom()
                        .withNewSecretKeyRef()
                            .withName(keystoreSecretName)
                            .withKey("user.password")
                        .endSecretKeyRef()
                    .endValueFrom()
                    .build()
            );
            // @formatter:on
            // spotless:on

            addSecretVolume(deployment, keystoreSecretName, KEYSTORE_SECRET_VOLUME_NAME);
            addSecretVolumeMount(deployment, containerName, KEYSTORE_SECRET_VOLUME_NAME,
                    "etc/" + KEYSTORE_SECRET_VOLUME_NAME);

            // ===== Truststore

            addEnvVar(env, ENV_KAFKASQL_SSL_TRUSTSTORE_TYPE, "PKCS12");
            addEnvVar(env, ENV_KAFKASQL_SSL_TRUSTSTORE_LOCATION,
                    "/etc/" + TRUSTSTORE_SECRET_VOLUME_NAME + "/ca.p12");
            // spotless:off
            // @formatter:off
            addEnvVar(env, new EnvVarBuilder()
                    .withName(ENV_KAFKASQL_SSL_TRUSTSTORE_PASSWORD)
                    .withNewValueFrom()
                        .withNewSecretKeyRef()
                            .withName(truststoreSecretName)
                            .withKey("ca.password")
                        .endSecretKeyRef()
                    .endValueFrom()
                    .build()
            );
            // @formatter:on
            // spotless:on

            addSecretVolume(deployment, truststoreSecretName, TRUSTSTORE_SECRET_VOLUME_NAME);
            addSecretVolumeMount(deployment, containerName, TRUSTSTORE_SECRET_VOLUME_NAME,
                    "etc/" + TRUSTSTORE_SECRET_VOLUME_NAME);

            return true;
        }
        return false;
    }

    public static void addSecretVolume(Deployment deployment, String secretName, String volumeName) {
        // spotless:off
        // @formatter:off
        deployment.getSpec().getTemplate().getSpec().getVolumes().add(
            new VolumeBuilder()
                .withName(volumeName)
                    .withNewSecret()
                        .withSecretName(secretName)
                    .endSecret()
            .build()
        );
        // @formatter:on
        // spotless:on
    }

    public static void addSecretVolumeMount(Deployment deployment, String containerName, String volumeName,
            String mountPath) {
        var c = getContainerFromDeployment(deployment, containerName);
        // spotless:off
        // @formatter:off
        c.getVolumeMounts().add(
            new VolumeMountBuilder()
                .withName(volumeName)
                .withReadOnly(true)
                .withMountPath(mountPath)
            .build()
        );
        // @formatter:on
        // spotless:on
    }
}
