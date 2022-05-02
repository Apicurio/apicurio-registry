package io.apicurio.registry.systemtest.registryinfra.resources;

public interface ResourceKind {
    String APICURIO_REGISTRY = "ApicurioRegistry";
    String NAMESPACE = "Namespace";
    String SERVICE = "Service";
    String DEPLOYMENT = "Deployment";
    String PERSISTENT_VOLUME_CLAIM = "PersistentVolumeClaim";
    String KAFKA = "Kafka";
    String KAFKA_TOPIC = "KafkaTopic";
    String KAFKA_USER = "KafkaUser";
    String KAFKA_CONNECT = "KafkaConnect";
    String ROUTE = "Route";
    String SECRET = "Secret";
}