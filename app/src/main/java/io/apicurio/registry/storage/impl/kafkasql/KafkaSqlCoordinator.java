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
 * Each pending operation is a single CompletableFuture in one map, so delivering the result and waking the
 * waiter are one step.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlCoordinator {

    @Inject
    Instance<KafkaSqlConfiguration> configuration;

    // Declared as ConcurrentHashMap rather than Map: the Byteman rule in KafkaSqlCoordinatorRaceTest
    // matches on ConcurrentHashMap.remove.
    private final ConcurrentHashMap<UUID, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    /**
     * Creates a UUID for a single operation and registers a pending entry under it. The
     * entry is removed by waitForResponse when a caller waits, or by forget when the send
     * fails; a caller that does neither leaks it.
     */
    public UUID createUUID() {
        UUID uuid = UUID.randomUUID();
        pending.put(uuid, new CompletableFuture<>());
        return uuid;
    }

    /**
     * Blocks until the operation with the given UUID completes or times out, then removes its entry.
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
            // No cause, deliberately: ProblemDetails.detail renders only the root cause, and the
            // TimeoutException from CompletableFuture.get() has a null message, so chaining it
            // would reduce detail to "TimeoutException: " and drop the operation UUID.
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Timed out waiting for a Kafka Sql response for operation " + uuid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        // a response on this node. Among the reasons: we're in a cluster and the HTTP
        // thread is on another node, we're starting up and consuming old journal entries,
        // or the entry was already removed (see createUUID). Dropping the response is
        // correct in all of them.
        CompletableFuture<Object> future = pending.get(uuid);
        if (future != null) {
            future.complete(returnValue);
        }
    }

    /**
     * Removes the entry without completing it, for a send that failed. Nobody can be waiting on
     * it: submitMessage hands the UUID to its caller only once the send has succeeded.
     */
    void forget(UUID uuid) {
        pending.remove(uuid);
    }

    /**
     * Test-only: the number of operations currently registered and not yet cleaned up.
     */
    int pendingCount() {
        return pending.size();
    }

}
