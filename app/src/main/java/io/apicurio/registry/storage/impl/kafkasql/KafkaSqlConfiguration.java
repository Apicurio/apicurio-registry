package io.apicurio.registry.storage.impl.kafkasql;

import java.util.Properties;

public interface KafkaSqlConfiguration {
    String bootstrapServers();

    String topic();

    String snapshotsTopic();

    String snapshotEvery();

    String snapshotLocation();

    Properties topicProperties();

    boolean isTopicAutoCreate();

    Integer pollTimeout();

    Integer responseTimeout();

    Properties producerProperties();

    Properties consumerProperties();

    Properties adminProperties();
}
