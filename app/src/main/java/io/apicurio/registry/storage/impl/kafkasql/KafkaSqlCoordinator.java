package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coordinates "write" responses across threads in the Kafka-SQL artifactStore implementation. Basically this
 * is used to communicate between the Kafka consumer thread and the waiting HTTP/API thread, where the HTTP
 * thread is waiting for an operation to be completed by the Kafka consumer thread.
 *
 * Uses a single ConcurrentHashMap of CompletableFuture to atomically associate "result ready" with "wake up
 * waiter", eliminating the dual-map desynchronization bugs that existed with the previous CountDownLatch +
 * returnValues approach.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlCoordinator {

    @Inject
    Instance<KafkaSqlConfiguration> configuration;

    private final ConcurrentHashMap<UUID, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    /**
     * Creates a UUID for a single operation.
     */
    public UUID createUUID() {
        UUID uuid = UUID.randomUUID();
        pending.put(uuid, new CompletableFuture<>());
        return uuid;
    }

    /**
     * Waits for a response to the operation with the given UUID. There is a CompletableFuture for each
     * operation. The caller waiting for the response will block until the future is completed and then
     * proceed. We also remove the future from the map here since it's not needed anymore.
     *
     * @param uuid
     */
    public Object waitForResponse(UUID uuid) {
        CompletableFuture<Object> future = pending.get(uuid);
        if (future == null) {
            throw new RegistryException(
                    "[KafkaSqlCoordinator] No pending operation for UUID " + uuid);
        }
        try {
            Object result = future.get(
                    configuration.get().getResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (result instanceof RuntimeException) {
                // Rethrow any RuntimeException to preserve the original exception type
                // for proper handling by exception mappers.
                throw (RuntimeException) result;
            }
            return result;
        } catch (TimeoutException e) {
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Timed out waiting for a Kafka Sql response for operation " + uuid);
        } catch (InterruptedException e) {
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Thread interrupted waiting for a Kafka Sql response.", e);
        } catch (ExecutionException e) {
            // Unreachable: notifyResponse uses complete(), never completeExceptionally().
            // Required because CompletableFuture.get() declares this checked exception.
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Error waiting for response.", e.getCause());
        } finally {
            pending.remove(uuid);
        }
    }

    /**
     * Complete the future for the given UUID. This will wake up the thread waiting for the response so that
     * it can proceed.
     *
     * @param uuid
     * @param returnValue
     */
    public void notifyResponse(UUID uuid, Object returnValue) {
        // we are re-using the topic from a streams based registry instance
        if (uuid == null) {
            return;
        }

        // If there is no pending future, then there is no HTTP thread waiting for
        // a response. This means one of two possible things:
        // 1) We're in a cluster and the HTTP thread is on another node
        // 2) We're starting up and consuming all the old journal entries
        CompletableFuture<Object> future = pending.get(uuid);
        if (future == null) {
            return;
        }

        // Otherwise, complete the future with the return value. This will
        // notify the HTTP thread that the operation is complete and there is
        // a return value waiting for it.
        future.complete(returnValue);
    }

    int pendingCount() {
        return pending.size();
    }

}
