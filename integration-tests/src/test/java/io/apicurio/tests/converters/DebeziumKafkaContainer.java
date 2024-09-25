package io.apicurio.tests.converters;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class DebeziumKafkaContainer {
    private static final String defaultImage = "confluentinc/cp-kafka:7.2.10";

    public static KafkaContainer defaultKRaftContainer(Network network) {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(defaultImage))
                .withNetwork(network).withKraft()) {
            return kafka;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KRaftContainer with default image.", e);
        }
    }

    public static KafkaContainer defaultKafkaContainer(Network network) {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(defaultImage))
                .withNetwork(network)) {
            return kafka;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KafkaContainer with default image.", e);
        }
    }
}
