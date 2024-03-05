package io.apicurio.deployment.manual;

import io.apicurio.deployment.k8s.bundle.KafkaBundle;

import static io.apicurio.deployment.k8s.PortForwardManager.startPortForward;
import static io.apicurio.deployment.k8s.PortForwardManager.stopPortForward;

public class ClusterKafkaRunner implements KafkaRunner {

    private final KafkaBundle kafka;

    private String testClientBootstrapServers;


    public ClusterKafkaRunner() {
        kafka = new KafkaBundle();
    }


    @Override
    public void startAndWait() {
        kafka.deploy();
        kafka.waitUtilReady();
    }


    @Override
    public String getBootstrapServers() {
        if(!kafka.isDeployed()) {
            throw new IllegalStateException("Not deployed.");
        }
        return String.format("PLAINTEXT://kafka-service:9092");
    }


    @Override
    public String getTestClientBootstrapServers() {
        if (testClientBootstrapServers == null) {
            startPortForward("kafka-service", 19092);
            testClientBootstrapServers = String.format("EXTERNAL://localhost:19092");
        }
        return testClientBootstrapServers;
    }


    @Override
    public void stopAndWait() {
        if (testClientBootstrapServers != null) {
            stopPortForward(19092);
            testClientBootstrapServers = null;
        }
        kafka.deleteAndWait();
    }
}
