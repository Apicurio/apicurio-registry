package io.apicurio.tests.utils;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

public class PulsarFacade implements AutoCloseable {
    static final Logger LOGGER = LoggerFactory.getLogger(io.apicurio.tests.utils.KafkaFacade.class);

    private PulsarClient client;

    private static PulsarFacade instance;

    PulsarContainer pulsarContainer;

    public static PulsarFacade getInstance() {
        if (instance == null) {
            instance = new PulsarFacade();
        }
        return instance;
    }

    private PulsarFacade() {
        // hidden constructor, singleton class
    }

    public String pulsarBrokerUrl() {
        return pulsarContainer.getPulsarBrokerUrl();
    }

    private boolean isRunning() {
        return pulsarContainer != null && pulsarContainer.isRunning();
    }

    public void startIfNeeded() {
        if (isRunning()) {
            LOGGER.info("Skipping deployment of kafka, because it's already deployed");
        } else {
            start();
        }
    }

    public void start() {
        if (isRunning()) {
            throw new IllegalStateException("Pulsar cluster is already running");
        }

        LOGGER.info("Starting pulsar container");
        this.pulsarContainer = new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.3.1"));
        pulsarContainer.start();
    }

    public void stopIfPossible() throws Exception {
        if (isRunning()) {
            close();
        }
    }

    public PulsarClient pulsarClient() throws PulsarClientException {
        if (client == null) {
            client = PulsarClient.builder().serviceUrl(pulsarBrokerUrl()).build();
        }
        return client;
    }

    @Override
    public void close() throws Exception {
        LOGGER.info("Stopping kafka container");
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
