package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.apicurio.registry.utils.Cell.cell;
import static org.awaitility.Awaitility.await;

/**
 * One-item non-thread-safe cache-like container that can be used to read and update a Kubernetes resource.
 */
public class K8sCell<T extends HasMetadata> {

    private static final Logger log = LoggerFactory.getLogger(K8sCell.class);

    private final KubernetesClient client;

    private T cachedValue;

    private final Supplier<T> initialReader;

    public static <T extends HasMetadata> K8sCell<T> k8sCell(KubernetesClient client, Supplier<T> initialReader) {
        return new K8sCell<>(client, initialReader);
    }

    public static <T extends HasMetadata> K8sCell<T> k8sCellCreate(KubernetesClient client, Supplier<T> initialCreator) {
        return new K8sCell<>(client, () -> {
            try {
                var r = initialCreator.get();
                r = client.resource(r).create();
                return r;
            } catch (Exception ex) {
                log.debug("Could not create resource", ex);
                return null;
            }
        });
    }

    private K8sCell(KubernetesClient client, Supplier<T> initialReader) {
        this.client = client;
        this.initialReader = initialReader;
    }

    public Optional<T> getOptional() {
        if (cachedValue == null) {
            cachedValue = initialReader.get();
        } else {
            cachedValue = client.resource(cachedValue).get();
        }
        return Optional.ofNullable(cachedValue);
    }

    public T get() {
        return getOptional().orElseThrow(() -> new IllegalStateException("Kubernetes resource does not exist."));
    }

    public T getCached() {
        if (cachedValue == null) {
            cachedValue = initialReader.get();
        }
        if (cachedValue == null) {
            throw new IllegalStateException("Kubernetes resource does not exist.");
        }
        return cachedValue;
    }

    public void update(Consumer<T> updater) {
        var first = cell(true);
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            try {
                if (first.get()) {
                    getCached();
                    first.set(false);
                } else {
                    get();
                }
                updater.accept(cachedValue);
                cachedValue = client.resource(cachedValue).update();
                return true;
            } catch (KubernetesClientException ex) {
                if (ex.getMessage().contains("the object has been modified")) {
                    log.debug("Retrying:", ex);
                    return false;
                } else {
                    throw ex;
                }
            }
        });
    }
}
