package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlTLSSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class KafkaSqlTLS {

    public static final String ENV_KAFKASQL_SECURITY_PROTOCOL = "APICURIO_KAFKA_COMMON_SECURITY_PROTOCOL";

    public static final String ENV_KAFKASQL_SSL_KEYSTORE_TYPE = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_TYPE";
    public static final String ENV_KAFKASQL_SSL_KEYSTORE_LOCATION = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_LOCATION";
    public static final String ENV_KAFKASQL_SSL_KEYSTORE_PASSWORD = "APICURIO_KAFKA_COMMON_SSL_KEYSTORE_PASSWORD";

    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_TYPE = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_TYPE";
    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_LOCATION = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_LOCATION";
    public static final String ENV_KAFKASQL_SSL_TRUSTSTORE_PASSWORD = "APICURIO_KAFKA_COMMON_SSL_TRUSTSTORE_PASSWORD";

    /**
     * Plain KafkaSQL must be already configured.
     */
    public static boolean configureKafkaSQLTLS(ApicurioRegistry3 primary, Deployment deployment,
            String containerName, Map<String, EnvVar> env) {

        // spotless:off
        var keystore = new SecretKeyRefTool(ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getKeystoreSecretRef)
                .orElse(null), "user.p12");

        var keystorePassword = new SecretKeyRefTool(ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getKeystorePasswordSecretRef)
                .orElse(null), "user.password");

        var truststore = new SecretKeyRefTool(ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getTruststoreSecretRef)
                .orElse(null), "ca.p12");

        var truststorePassword = new SecretKeyRefTool(ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls)
                .map(KafkaSqlTLSSpec::getTruststorePasswordSecretRef)
                .orElse(null), "ca.password");
        // spotless:on

        if (truststore.isValid() && truststorePassword.isValid() && keystore.isValid()
                && keystorePassword.isValid()) {

            addEnvVar(env, ENV_KAFKASQL_SECURITY_PROTOCOL, "SSL");

            // ===== Keystore

            addEnvVar(env, ENV_KAFKASQL_SSL_KEYSTORE_TYPE, "PKCS12");
            keystore.applySecretVolume(deployment, containerName);
            addEnvVar(env, ENV_KAFKASQL_SSL_KEYSTORE_LOCATION, keystore.getSecretVolumeKeyPath());
            keystorePassword.applySecretEnvVar(env, ENV_KAFKASQL_SSL_KEYSTORE_PASSWORD);

            // ===== Truststore

            addEnvVar(env, ENV_KAFKASQL_SSL_TRUSTSTORE_TYPE, "PKCS12");
            truststore.applySecretVolume(deployment, containerName);
            addEnvVar(env, ENV_KAFKASQL_SSL_TRUSTSTORE_LOCATION, truststore.getSecretVolumeKeyPath());
            truststorePassword.applySecretEnvVar(env, ENV_KAFKASQL_SSL_TRUSTSTORE_PASSWORD);

            return true;
        }
        return false;
    }
}
