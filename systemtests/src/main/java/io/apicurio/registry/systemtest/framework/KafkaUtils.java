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
        String kafkaCaSecretName = kafkaName + "-cluster-ca-cert";

        // Wait for cluster CA secret
        if (waitSecretReady(namespace, kafkaCaSecretName)) {
            LOGGER.info("Secret with name {} present in namespace {}.", kafkaCaSecretName, namespace);
        } else {
            LOGGER.info("Secret with name {} is not present in namespace {}.", kafkaCaSecretName, namespace);
        }

        ResourceManager.getInstance().createResource(
                testContext,
                true,
                KafkaUserResourceType.getDefaultByKind(username, namespace, kafkaName, kind)
        );
    }

    public static Kafka deployDefaultKafkaByKind(ExtensionContext testContext, KafkaKind kind) {
        Kafka kafka = KafkaResourceType.getDefaultByKind(kind);

        ResourceManager.getInstance().createResource(testContext, true, kafka);

        if (KafkaKind.SCRAM.equals(kind) || KafkaKind.TLS.equals(kind)) {
            createSecuredUser(testContext, Constants.KAFKA_USER, kafka, kind);
        }

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
