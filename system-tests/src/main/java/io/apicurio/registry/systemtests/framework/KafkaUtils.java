package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaUserResourceType;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
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
            LOGGER.error("Secret {} in namespace {} failed readiness check.", name, namespace);

            return false;
        }

        return true;
    }

    public static void createSecuredUser(ExtensionContext testContext, String username, Kafka kafka, KafkaKind kind) throws InterruptedException {
        String namespace = kafka.getMetadata().getNamespace();
        String kafkaName = kafka.getMetadata().getName();
        String kafkaCaSecretName = kafkaName + "-cluster-ca-cert";

        // Wait for cluster CA secret
        if (waitSecretReady(namespace, kafkaCaSecretName)) {
            LOGGER.info("Secret with name {} present in namespace {}.", kafkaCaSecretName, namespace);
        } else {
            LOGGER.error("Secret with name {} is not present in namespace {}.", kafkaCaSecretName, namespace);
        }

        ResourceManager.getInstance().createResource(
                true,
                KafkaUserResourceType.getDefaultByKind(username, namespace, kafkaName, kind)
        );
    }

    public static Kafka deployDefaultKafkaByKind(ExtensionContext testContext, KafkaKind kind) throws InterruptedException {
        Kafka kafka = KafkaResourceType.getDefaultByKind(kind);

        ResourceManager.getInstance().createResource(true, kafka);

        if (KafkaKind.SCRAM.equals(kind) || KafkaKind.TLS.equals(kind)) {
            createSecuredUser(testContext, Constants.KAFKA_USER, kafka, kind);
        }

        /* Update of bootstrap server to use secured port is handled in ApicurioRegistryResourceType */

        return kafka;
    }

    public static Kafka deployDefaultKafkaNoAuth(ExtensionContext testContext) throws InterruptedException {
        return deployDefaultKafkaByKind(testContext, KafkaKind.NO_AUTH);
    }

    public static Kafka deployDefaultKafkaTls(ExtensionContext testContext) throws InterruptedException {
        return deployDefaultKafkaByKind(testContext, KafkaKind.TLS);
    }

    public static Kafka deployDefaultKafkaScram(ExtensionContext testContext) throws InterruptedException {
        return deployDefaultKafkaByKind(testContext, KafkaKind.SCRAM);
    }
}
