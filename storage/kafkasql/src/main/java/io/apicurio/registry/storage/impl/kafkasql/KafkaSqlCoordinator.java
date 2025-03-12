/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessCheck;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.types.RegistryException;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Coordinates "write" responses across threads in the Kafka-SQL artifactStore implementation.  Basically this is used
 * to communicate between the Kafka consumer thread and the waiting HTTP/API thread, where the HTTP thread is
 * waiting for an operation to be completed by the Kafka consumer thread.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class KafkaSqlCoordinator {

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    Logger log;

    @Inject
    LivenessUtil livenessUtil;

    @Inject
    PersistenceExceptionLivenessCheck livenessCheck;

    private static final Object NULL = new Object();

    private final Map<UUID, WaitingOperation> waitingOperations = new ConcurrentHashMap<>();

    private volatile Instant lastLeakCheck = Instant.now();

    /**
     * Creates a UUID for a single operation.
     * <p>
     * Arguments are used to provide context in case of an error.
     */
    public UUID createUUID(MessageKey key, ActionType actionType) {
        var operation = new WaitingOperation(key, actionType);
        waitingOperations.put(operation.getUuid(), operation);
        return operation.getUuid();
    }

    public boolean isOurWaitingOperation(UUID uuid) {
        return waitingOperations.containsKey(uuid);
    }

    /**
     * Waits for a response to the operation with the given UUID. There is a countdown latch for each operation.  The
     * caller waiting for the response will wait for the countdown to happen and then proceed.  We also remove
     * the latch from the Map here since it's not needed anymore.
     *
     * @param uuid
     * @throws InterruptedException
     */
    public Object waitForResponse(UUID uuid) {
        try {
            var operation = waitingOperations.get(uuid);
            operation.getLatch().await(configuration.responseTimeout(), TimeUnit.MILLISECONDS);

            Object rval = operation.getReturnValue();
            if (rval == NULL) {
                return null;
            } else if (rval instanceof RegistryException) {
                throw (RegistryException) rval;
            } else if (rval instanceof Exception) {
                throw new RegistryException((Exception) rval);
            }
            return rval;
        } catch (InterruptedException e) {
            throw new RegistryException("[KafkaSqlCoordinator] Thread interrupted waiting for a Kafka Sql response.", e);
        } finally {
            waitingOperations.remove(uuid);
        }
    }

    @Scheduled(delay = 1, concurrentExecution = SKIP, every = "10s")
    void checkLeaks() {
        var now = Instant.now();
        if (now.isAfter(lastLeakCheck.plus(configuration.responseTimeout() * 2L, ChronoUnit.MILLIS))) {
            var leaks = waitingOperations.values().stream()
                    .filter(op -> now.isAfter(op.getCreatedAt().plus(configuration.responseTimeout() * 2L, ChronoUnit.MILLIS)))
                    .collect(Collectors.toList());
            leaks.forEach(leak -> {
                var returnValue = leak.getReturnValue();
                if (returnValue != null && returnValue != NULL) {
                    // See also: io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlSink.processMessage
                    if (returnValue instanceof Exception && !livenessUtil.isIgnoreError((Exception) returnValue)) {
                        log.warn("An exception was ignored when processing KafkaSql message (initiated by this node). " +
                                 "Key = '" + leak.getKey() + "'" +
                                 (leak.getActionType() != null ? ", action = '" + leak.getActionType() + "'" : ""), (Exception) returnValue);
                        livenessCheck.suspectWithException((Exception) returnValue);
                    } else {
                        log.debug("A return value was ignored when processing KafkaSql message (initiated by this node). " +
                                  "Key = '" + leak.getKey() + "'" +
                                  (leak.getActionType() != null ? ", action = '" + leak.getActionType() + "'" : "") +
                                  ", return value = '" + returnValue + "'.");
                    }
                }
                waitingOperations.remove(leak.getUuid());
            });
            lastLeakCheck = now;
        }
    }

    /**
     * Countdown the latch for the given UUID.  This will wake up the thread waiting for the response
     * so that it can proceed.
     *
     * @param uuid
     * @param returnValue
     */
    public void notifyResponse(UUID uuid, Object returnValue) {
        //we are re-using the topic from a streams based registry instance
        if (uuid == null) {
            return;
        }

        var operation = waitingOperations.get(uuid);

        // If there is no countdown latch, then there is no HTTP thread waiting for
        // a response.  This means one of two possible things:
        //  1) We're in a cluster and the HTTP thread is on another node
        //  2) We're starting up and consuming all the old journal entries
        if (operation == null) {
            return;
        }

        // Otherwise, put the return value in the Map and countdown the latch.  The latch
        // countdown will notify the HTTP thread that the operation is complete and there is
        // a return value waiting for it.
        if (returnValue == null) {
            returnValue = NULL;
        }
        operation.setReturnValue(returnValue);
        operation.getLatch().countDown();
    }
}
