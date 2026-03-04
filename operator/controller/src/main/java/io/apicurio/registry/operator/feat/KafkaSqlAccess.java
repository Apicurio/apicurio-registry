package io.apicurio.registry.operator.feat;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;

import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;

/**
 * Configures KafkaSQL storage from a Strimzi Kafka Access Operator secret.
 * <p>
 * The secret (type {@code servicebinding.io/kafka}) contains keys such as
 * {@code bootstrapServers}, {@code security.protocol}, {@code ssl.truststore.crt},
 * {@code ssl.keystore.crt}, {@code ssl.keystore.key}, {@code sasl.jaas.config},
 * and {@code sasl.mechanism}. Environment variables are set via {@code valueFrom.secretKeyRef}
 * so that credentials never leave Kubernetes secrets.
 * <p>
 * The secret is not read at reconciliation time. All env vars are wired with
 * {@code optional: true} for non-required keys, so that Kubernetes resolves the
 * values at pod startup. This avoids RBAC exposure and ensures the storage kind
 * is always set to {@code kafkasql} even if the secret is not yet available.
 */
public class KafkaSqlAccess {

    public static void configureKafkaSQLFromAccessSecret(Map<String, EnvVar> env, String secretName) {
        addEnvVar(env, new EnvVarBuilder().withName(KafkaSql.ENV_STORAGE_KIND).withValue("kafkasql").build());

        // Required: bootstrap servers
        addSecretKeyRefEnvVar(env, KafkaSql.ENV_KAFKASQL_BOOTSTRAP_SERVERS, secretName, "bootstrapServers",
                false);

        // Optional: security protocol
        addSecretKeyRefEnvVar(env, KAFKASQL_SECURITY_PROTOCOL, secretName, "security.protocol", true);

        // PEM-based truststore (optional)
        addEnvVar(env, new EnvVarBuilder().withName(KAFKASQL_SSL_TRUSTSTORE_TYPE).withValue("PEM").build());
        addSecretKeyRefEnvVar(env, KAFKASQL_SSL_TRUSTSTORE_CERTIFICATES, secretName, "ssl.truststore.crt",
                true);

        // PEM-based keystore (optional)
        addEnvVar(env, new EnvVarBuilder().withName(KAFKASQL_SSL_KEYSTORE_TYPE).withValue("PEM").build());
        addSecretKeyRefEnvVar(env, KAFKASQL_SSL_KEYSTORE_CERTIFICATE_CHAIN, secretName, "ssl.keystore.crt",
                true);
        addSecretKeyRefEnvVar(env, KAFKASQL_SSL_KEYSTORE_KEY, secretName, "ssl.keystore.key", true);

        // SASL/SCRAM authentication (optional)
        addSecretKeyRefEnvVar(env, KAFKASQL_SASL_JAAS_CONFIG, secretName, "sasl.jaas.config", true);
        addSecretKeyRefEnvVar(env, KAFKASQL_SASL_MECHANISM, secretName, "sasl.mechanism", true);
    }

    private static void addSecretKeyRefEnvVar(Map<String, EnvVar> env, String envVarName,
            String secretName, String secretKey, boolean optional) {
        addEnvVar(env, new EnvVarBuilder()
                .withName(envVarName)
                .withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder()
                                .withName(secretName)
                                .withKey(secretKey)
                                .withOptional(optional)
                                .build())
                        .build())
                .build());
    }
}
