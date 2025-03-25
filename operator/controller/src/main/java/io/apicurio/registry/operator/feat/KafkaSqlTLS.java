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
import java.util.Optional;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;
import static java.util.Optional.ofNullable;

public class KafkaSqlTLS {

    /**
     * Plain KafkaSQL must be already configured.
     */
    public static boolean configureKafkaSQLTLS(ApicurioRegistry3 primary, Deployment deployment,
            String containerName, Map<String, EnvVar> env) {

        var keystore = new SecretKeyRefTool(getKafkaSqlTLSSpec(primary)
                .map(KafkaSqlTLSSpec::getKeystoreSecretRef)
                .orElse(null), "user.p12");

        var keystorePassword = new SecretKeyRefTool(getKafkaSqlTLSSpec(primary)
                .map(KafkaSqlTLSSpec::getKeystorePasswordSecretRef)
                .orElse(null), "user.password");

        var truststore = new SecretKeyRefTool(getKafkaSqlTLSSpec(primary)
                .map(KafkaSqlTLSSpec::getTruststoreSecretRef)
                .orElse(null), "ca.p12");

        var truststorePassword = new SecretKeyRefTool(getKafkaSqlTLSSpec(primary)
                .map(KafkaSqlTLSSpec::getTruststorePasswordSecretRef)
                .orElse(null), "ca.password");

        boolean configured = false;

        if (truststore.isValid() && truststorePassword.isValid()) {
            // ===== Truststore

            addEnvVar(env, KAFKASQL_SSL_TRUSTSTORE_TYPE, "PKCS12");
            truststore.applySecretVolume(deployment, containerName);
            addEnvVar(env, KAFKASQL_SSL_TRUSTSTORE_LOCATION, truststore.getSecretVolumeKeyPath());
            truststorePassword.applySecretEnvVar(env, KAFKASQL_SSL_TRUSTSTORE_PASSWORD);

            configured = true;
        }

        if (keystore.isValid()
                && keystorePassword.isValid()) {
            // ===== Keystore

            addEnvVar(env, KAFKASQL_SSL_KEYSTORE_TYPE, "PKCS12");
            keystore.applySecretVolume(deployment, containerName);
            addEnvVar(env, KAFKASQL_SSL_KEYSTORE_LOCATION, keystore.getSecretVolumeKeyPath());
            keystorePassword.applySecretEnvVar(env, KAFKASQL_SSL_KEYSTORE_PASSWORD);

            configured = true;
        }

        return configured;
    }

    private static Optional<KafkaSqlTLSSpec> getKafkaSqlTLSSpec(ApicurioRegistry3 primary) {
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getTls);
    }
}
