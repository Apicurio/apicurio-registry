package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates "write" responses across threads in the Kafka-SQL artifactStore implementation. Basically this
 * is used to communicate between the Kafka consumer thread and the waiting HTTP/API thread, where the HTTP
 * thread is waiting for an operation to be completed by the Kafka consumer thread.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlCoordinator {

    @Inject
    Instance<KafkaSqlConfiguration> configuration;

    private Map<UUID, CompletableFuture<Object>> operations = new ConcurrentHashMap<>();

    /**
     * Creates a UUID for a single operation.
     */
    public UUID createUUID() {
        UUID uuid = UUID.randomUUID();
        operations.put(uuid, new CompletableFuture<>());
        return uuid;
    }

    /**
     * Waits for a response to the operation with the given UUID.
     *
     * @param uuid
     * @throws RegistryException
     */
    public Object waitForResponse(UUID uuid) {
        try {
            CompletableFuture<Object> future = operations.get(uuid);
            if (future == null) {
                throw new RegistryException(
                        "[KafkaSqlCoordinator] Timeout waiting for a Kafka Sql response from consumer thread.");
            }
            return future.get(configuration.get().getResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Timeout waiting for a Kafka Sql response from consumer thread.", e);
        } catch (InterruptedException e) {
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Thread interrupted waiting for a Kafka Sql response.", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RegistryException("Error waiting for Kafka Sql response", cause);
        } finally {
            operations.remove(uuid);
        }
    }

    /**
     * Completes the operation for the given UUID, notifying the waiting thread.
     *
     * @param uuid
     * @param returnValue
     */
    public void notifyResponse(UUID uuid, Object returnValue) {
        if (uuid == null) {
            return;
        }

        CompletableFuture<Object> future = operations.get(uuid);
        if (future == null) {
            return;
        }

        if (returnValue instanceof Throwable) {
            // Replicate the previous behavior where the consumer could pass an exception as a return value,
            // but now we complete the future exceptionally.
            future.completeExceptionally((Throwable) returnValue);
        } else {
            future.complete(returnValue);
        }
    }

}
