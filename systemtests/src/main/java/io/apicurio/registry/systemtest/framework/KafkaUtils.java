package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaKind;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaTopicResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaUserResourceType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaTopic;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;

public class KafkaUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ResourceManager resourceManager = ResourceManager.getInstance();

    private static boolean waitSecretReady(String namespace, String name) {
        return waitSecretReady(namespace, name, TimeoutBudget.ofDuration(Duration.ofMinutes(1)));
    }

    private static boolean waitSecretReady(String namespace, String name, TimeoutBudget timeoutBudget) {
        while (!timeoutBudget.timeoutExpired()) {
            if (Kubernetes.getClient().secrets().inNamespace(namespace).withName(name).get() != null) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        boolean pass = Kubernetes.getClient().secrets().inNamespace(namespace).withName(name).get() != null;

        if (!pass) {
            LOGGER.info("Secret {} in namespace {} failed readiness check.", name, namespace);
        }

        return pass;
    }

    public static Kafka deployDefaultKafkaByKind(ExtensionContext testContext, KafkaKind kafkaKind) {
        Kafka kafka;
        ArrayList<KafkaTopic> topicList = new ArrayList<>();
        String kafkaUserUsername = null;

        if(KafkaKind.NO_AUTH.equals(kafkaKind)) {
            kafka = KafkaResourceType.getDefaultNoAuth();
        } else if(KafkaKind.TLS.equals(kafkaKind)) {
            kafka = KafkaResourceType.getDefaultTLS();

            kafkaUserUsername = "apicurio-registry-kafka-user-secured-tls";
        } else if(KafkaKind.SCRAM.equals(kafkaKind)) {
            kafka = KafkaResourceType.getDefaultSCRAM();

            kafkaUserUsername = "apicurio-registry-kafka-user-secured-scram";
        } else {
            throw new IllegalStateException("Unexpected value: " + kafkaKind);
        }

        try {
            resourceManager.createResource(testContext, true, kafka);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Add test topic
        topicList.add(KafkaTopicResourceType.getDefault("kafka-topic-test", kafka.getMetadata().getNamespace(), kafka.getMetadata().getName()));

        // Create Kafka topic(s)
        for(KafkaTopic kafkaTopic : topicList) {
            try {
                resourceManager.createResource(testContext, true, kafkaTopic);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(KafkaKind.TLS.equals(kafkaKind) || KafkaKind.SCRAM.equals(kafkaKind)) {
            // Create secured Kafka user
            try {
                resourceManager.createResource(testContext, true, KafkaUserResourceType.getDefaultByKind(kafkaUserUsername, kafka.getMetadata().getNamespace(), kafka.getMetadata().getName(), kafkaKind));
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Wait for cluster CA secret
            if(waitSecretReady(kafka.getMetadata().getNamespace(), kafka.getMetadata().getName() + "-cluster-ca-cert")) {
                LOGGER.info("Secret with name {} created in namespace {}.", kafka.getMetadata().getName() + "-cluster-ca-cert", kafka.getMetadata().getNamespace());
            } else {
                LOGGER.info("Secret with name {} is not created in namespace {}.", kafka.getMetadata().getName() + "-cluster-ca-cert", kafka.getMetadata().getNamespace());
            }

            // Update of bootstrap server to use secured port is handled in ApicurioRegistryResourceType
        }

        return kafka;
    }

    public static Kafka deployDefaultKafkaNoAuth(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.NO_AUTH);
    }

    public static Kafka deployDefaultKafkaTLS(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.TLS);
    }

    public static Kafka deployDefaultKafkaSCRAM(ExtensionContext testContext) {
        return deployDefaultKafkaByKind(testContext, KafkaKind.SCRAM);
    }
}
