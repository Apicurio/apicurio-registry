package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class KafkaSqlAccess {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlAccess.class);

    public static void configureKafkaSQLFromAccessSecret(ApicurioRegistry3 primary, Deployment deployment,
            Map<String, EnvVar> env, KubernetesClient client, String secretName) {

        var namespace = primary.getMetadata().getNamespace();
        var secret = client.secrets().inNamespace(namespace).withName(secretName).get();

        if (secret == null) {
            log.warn("KafkaAccess secret '{}' not found in namespace '{}'. "
                    + "KafkaSQL storage will not be configured. The operator will retry on the next reconciliation.",
                    secretName, namespace);
            return;
        }

        var data = secret.getData();
        if (data == null || !data.containsKey("bootstrapServers")) {
            log.warn("KafkaAccess secret '{}' does not contain the required 'bootstrapServers' key. "
                    + "KafkaSQL storage will not be configured.", secretName);
            return;
        }

        addEnvVar(env, new EnvVarBuilder().withName(KafkaSql.ENV_STORAGE_KIND).withValue("kafkasql").build());
        addSecretKeyRefEnvVar(env, KafkaSql.ENV_KAFKASQL_BOOTSTRAP_SERVERS, secretName, "bootstrapServers");

        if (data.containsKey("security.protocol")) {
            addSecretKeyRefEnvVar(env, KAFKASQL_SECURITY_PROTOCOL, secretName, "security.protocol");
        }

        // PEM-based truststore
        if (data.containsKey("ssl.truststore.crt")) {
            addEnvVar(env, new EnvVarBuilder().withName(KAFKASQL_SSL_TRUSTSTORE_TYPE).withValue("PEM").build());
            addSecretKeyRefEnvVar(env, KAFKASQL_SSL_TRUSTSTORE_CERTIFICATES, secretName, "ssl.truststore.crt");
        }

        // PEM-based keystore
        if (data.containsKey("ssl.keystore.crt") && data.containsKey("ssl.keystore.key")) {
            addEnvVar(env, new EnvVarBuilder().withName(KAFKASQL_SSL_KEYSTORE_TYPE).withValue("PEM").build());
            addSecretKeyRefEnvVar(env, KAFKASQL_SSL_KEYSTORE_CERTIFICATE_CHAIN, secretName, "ssl.keystore.crt");
            addSecretKeyRefEnvVar(env, KAFKASQL_SSL_KEYSTORE_KEY, secretName, "ssl.keystore.key");
        }

        // SASL/SCRAM authentication
        if (data.containsKey("sasl.jaas.config")) {
            addSecretKeyRefEnvVar(env, KAFKASQL_SASL_JAAS_CONFIG, secretName, "sasl.jaas.config");
        }

        if (data.containsKey("sasl.mechanism")) {
            addSecretKeyRefEnvVar(env, KAFKASQL_SASL_MECHANISM, secretName, "sasl.mechanism");
        }
    }

    private static void addSecretKeyRefEnvVar(Map<String, EnvVar> env, String envVarName,
            String secretName, String secretKey) {
        addEnvVar(env, new EnvVarBuilder()
                .withName(envVarName)
                .withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder()
                                .withName(secretName)
                                .withKey(secretKey)
                                .build())
                        .build())
                .build());
    }
}
