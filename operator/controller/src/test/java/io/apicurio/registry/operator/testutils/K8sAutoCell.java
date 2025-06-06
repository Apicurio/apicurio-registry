package io.apicurio.registry.operator.testutils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.apicurio.registry.operator.testutils.Utils.updateWithRetries;
import static io.apicurio.registry.operator.utils.Utils.isBlank;

/**
 * One-item non-thread-safe cache-like container that can be used to read and update a Kubernetes resource.
 */
public class K8sAutoCell<T extends HasMetadata> {

    private T cachedValue;

    private final KubernetesClient client;

    private final Supplier<T> initializer;

    /**
     * Create a new Kubernetes auto cell with the specified initializer function.
     */
    public static <T extends HasMetadata> K8sAutoCell<T> kacell(KubernetesClient client, Supplier<T> initializer) {
        return new K8sAutoCell<>(client, initializer);
    }

    private K8sAutoCell(KubernetesClient client, Supplier<T> initializer) {
        this.client = client;
        this.initializer = initializer;
    }

    public T get() {
        if (cachedValue == null) {
            cachedValue = initializer.get();
        }
        check();
        cachedValue = client.resource(cachedValue).get();
        return cachedValue;
    }

    public T getCached() {
        return cachedValue;
    }

    public void update(Consumer<T> action) {
        updateWithRetries(() -> {
            get();
            check();
            action.accept(cachedValue);
            cachedValue = client.resource(cachedValue).update();
        });
    }

    private void check() {
        if (cachedValue == null || cachedValue.getMetadata() == null || isBlank(cachedValue.getMetadata().getName())) {
            throw new IllegalStateException("Could not retrieve Kubernetes resource. Cached value is null or invalid.");
        }
    }
}
