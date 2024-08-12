package io.apicurio.registry.operator.test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Builder;
import lombok.Getter;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class OperatorTestExtension {

    private static final Logger log = LoggerFactory.getLogger(OperatorTestExtension.class);

    public static final long WAIT_SECS = 90;
    public static final Duration WAIT = ofSeconds(WAIT_SECS);
    public static final long LONG_WAIT_SECS = 3 * WAIT_SECS;
    public static final Duration LONG_WAIT = ofSeconds(LONG_WAIT_SECS);

    @Getter
    private String namespace;

    @Getter
    private KubernetesClient client;

    private final AtomicInteger nextLocalPort = new AtomicInteger(55001);

    private final Map<Integer, LocalPortForward> portForwardMap = new ConcurrentHashMap<>();

    private boolean isStarted;
    private boolean isStopped;

    @Builder
    private OperatorTestExtension() {
    }

    public synchronized void start() {
        if (!isStarted) {
            doBeforeAll();
            isStarted = true;
        }
    }

    private void doBeforeAll() {
        Awaitility.setDefaultPollInterval(ofSeconds(3));
        Awaitility.setDefaultTimeout(WAIT);

        client = CDI.current().select(KubernetesClient.class).get();
        requireNonNull(client);

        namespace = "apicurio-registry-operator-test-" + UUID.randomUUID().toString().substring(0, 8);
        client.namespaces()
                .resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
                .create();
        client.namespaces().withName(namespace).waitUntilCondition(Objects::nonNull, WAIT_SECS, SECONDS);
    }

    public synchronized void stop() {
        if (!isStopped) {
            doAfterAll();
            isStopped = true;
        }
    }

    private void doAfterAll() {
        portForwardMap.values().forEach(pf -> {
            try {
                pf.close();
            } catch (IOException ex) {
                log.error("Could not close port forward " + pf, ex);
            }
        });
        client.namespaces().withName(namespace).delete();
        client.namespaces().withName(namespace).waitUntilCondition(Objects::isNull, LONG_WAIT_SECS, SECONDS);
    }

    public <T extends HasMetadata> T create(T resource) {
        var res = client.resource(resource).inNamespace(namespace).create();
        isPresent(res.getClass(), res.getMetadata().getName());
        return res;
    }

    public <T extends HasMetadata> void expect(Class<T> type, String name, Consumer<T> action) {
        await().untilAsserted(() -> {
            var res = client.resources(type).inNamespace(namespace).withName(name).get();
            assertThat(res).isNotNull();
            action.accept(res);
        });
    }

    public <T extends HasMetadata> void isPresent(Class<T> type, String name) {
        client.resources(type).inNamespace(namespace).withName(name).waitUntilCondition(Objects::nonNull,
                WAIT_SECS, SECONDS);
        if (Deployment.class.equals(type)) {
            client.resources(type).inNamespace(namespace).withName(name).waitUntilReady(WAIT_SECS, SECONDS);
        }
    }

    public <T extends HasMetadata> void isNotPresent(Class<T> type, String name) {
        client.resources(type).inNamespace(namespace).withName(name).waitUntilCondition(Objects::isNull,
                WAIT_SECS, SECONDS);
    }

    public <T extends HasMetadata> void delete(T resource) {
        client.resource(resource).inNamespace(namespace).delete();
        client.resource(resource).inNamespace(namespace).waitUntilCondition(Objects::isNull, WAIT_SECS,
                SECONDS);
    }

    public <T extends HasMetadata> void delete(Class<T> type, String name) {
        client.resources(type).inNamespace(namespace).withName(name).delete();
        client.resources(type).inNamespace(namespace).withName(name).waitUntilCondition(Objects::isNull,
                WAIT_SECS, SECONDS);
    }

    public int portForward(String targetService, int targetPort) {
        int localPort = nextLocalPort.getAndIncrement();
        var pf = client.services().inNamespace(namespace).withName(targetService).portForward(targetPort,
                localPort);
        portForwardMap.put(localPort, pf);
        return localPort;
    }
}
