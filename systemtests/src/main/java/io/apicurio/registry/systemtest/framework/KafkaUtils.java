package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaUserResourceType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.time.Duration;

public class KafkaUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ResourceManager RESOURCE_MANAGER = ResourceManager.getInstance();

    private static boolean waitSecretReady(String namespace, String name) {
        return waitSecretReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(1)));
    }

    private static boolean waitSecretReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getSecret(namespace, name) != null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (Kubernetes.getSecret(namespace, name) == null) {
            LOGGER.info("Secret {} in namespace {} failed readiness check.", name, namespace);

            return false;
        }

        return true;
    }

    public static void createSecuredUser(ExtensionContext testContext, String username, Kafka kafka, KafkaKind kind) {
        String namespace = kafka.getMetadata().getNamespace();
        String kafkaName = kafka.getMetadata().getName();

        RESOURCE_MANAGER.createResource(
                testContext,
                true,
                KafkaUserResourceType.getDefaultByKind(username, namespace, kafkaName, kind)
        );

        String kafkaCaSecretName = kafkaName + "-cluster-ca-cert";

        // Wait for cluster CA secret
        if (waitSecretReady(namespace, kafkaCaSecretName)) {
            LOGGER.info("Secret with name {} created in namespace {}.", kafkaCaSecretName, namespace);
        } else {
            LOGGER.info("Secret with name {} is not created in namespace {}.", kafkaCaSecretName, namespace);
        }
    }

    public static Kafka deployDefaultKafkaByKind(ExtensionContext testContext, KafkaKind kind) {
        Kafka kafka;

        if (KafkaKind.NO_AUTH.equals(kind)) {
            kafka = KafkaResourceType.getDefaultNoAuth();
        } else if (KafkaKind.TLS.equals(kind)) {
            kafka = KafkaResourceType.getDefaultTLS();

            createSecuredUser(testContext, "apicurio-registry-kafka-user-secured-tls", kafka, kind);
        } else if (KafkaKind.SCRAM.equals(kind)) {
            kafka = KafkaResourceType.getDefaultSCRAM();

            createSecuredUser(testContext, "apicurio-registry-kafka-user-secured-scram", kafka, kind);
        } else {
            throw new IllegalStateException("Unexpected value: " + kind);
        }

        RESOURCE_MANAGER.createResource(testContext, true, kafka);

        /* Update of bootstrap server to use secured port is handled in ApicurioRegistryResourceType */

        return kafka;
    }

    public static Kafka deployDefaultKafkaNoAuth(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.NO_AUTH);
    }

    public static Kafka deployDefaultKafkaTls(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.TLS);
    }

    public static Kafka deployDefaultKafkaScram(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.SCRAM);
    }
}
