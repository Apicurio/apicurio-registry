package io.apicurio.deployment.manual;

public interface KafkaRunner {

    void startAndWait();

    String getBootstrapServers();

    String getTestClientBootstrapServers();

    void stopAndWait();
}
