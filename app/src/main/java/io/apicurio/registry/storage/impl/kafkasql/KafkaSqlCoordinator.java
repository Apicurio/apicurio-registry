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

    private final Map<UUID, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

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
     * Throws a RegistryException immediately when nothing is registered under the UUID.
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
            // Suppressed rather than chained: the error detail renders the root cause message,
            // and this exception's null message would hide the operation UUID.
            RegistryException timeout = new RegistryException(
                    "[KafkaSqlCoordinator] Timed out waiting for a Kafka Sql response for operation " + uuid);
            timeout.addSuppressed(e);
            throw timeout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Suppressed rather than chained, for the same reason as the timeout above.
            RegistryException interrupted = new RegistryException(
                    "[KafkaSqlCoordinator] Thread interrupted waiting for a Kafka Sql response for operation " + uuid);
            interrupted.addSuppressed(e);
            throw interrupted;
        } catch (ExecutionException e) {
            // Unreachable today: notifyResponse never calls completeExceptionally().
            throw new RegistryException(
                    "[KafkaSqlCoordinator] Error waiting for a Kafka Sql response for operation " + uuid,
                    e.getCause());
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

        // No entry means nobody on this node is waiting (another node's request, journal
        // replay at startup, or already cleaned up), so the response is dropped.
        CompletableFuture<Object> future = pending.get(uuid);
        if (future != null) {
            // First response wins: complete() ignores any later notify.
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
