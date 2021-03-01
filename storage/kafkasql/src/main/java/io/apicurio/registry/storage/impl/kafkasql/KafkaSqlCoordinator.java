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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.types.RegistryException;

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

    private static final Object NULL = new Object();
    private Map<UUID, CountDownLatch> latches = new ConcurrentHashMap<>();
    private Map<UUID, Object> returnValues = new ConcurrentHashMap<>();

    /**
     * Creates a UUID for a single operation.
     */
    public UUID createUUID() {
        UUID uuid = UUID.randomUUID();
        latches.put(uuid, new CountDownLatch(1));
        return uuid;
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
            latches.get(uuid).await(configuration.responseTimeout(), TimeUnit.MILLISECONDS);

            Object rval = returnValues.remove(uuid);
            if (rval == NULL) {
                return null;
            } else if (rval instanceof RegistryException) {
                throw (RegistryException) rval;
            }
            return rval;
        } catch (InterruptedException e) {
          throw new RegistryException("[KafkaSqlCoordinator] Thread interrupted waiting for a Kafka Sql response.", e);
        } finally {
            latches.remove(uuid);
        }
    }

    /**
     * Countdown the latch for the given UUID.  This will wake up the thread waiting for the response
     * so that it can proceed.
     * @param uuid
     * @param returnValue
     */
    public void notifyResponse(UUID uuid, Object returnValue) {
        //we are re-using the topic from a streams based registry instance
        if (uuid == null) {
            return;
        }

        // If there is no countdown latch, then there is no HTTP thread waiting for
        // a response.  This means one of two possible things:
        //  1) We're in a cluster and the HTTP thread is on another node
        //  2) We're starting up and consuming all the old journal entries
        if (!latches.containsKey(uuid)) {
            return;
        }

        // Otherwise, put the return value in the Map and countdown the latch.  The latch
        // countdown will notify the HTTP thread that the operation is complete and there is
        // a return value waiting for it.
        if (returnValue == null) {
            returnValue = NULL;
        }
        returnValues.put(uuid, returnValue);
        latches.get(uuid).countDown();
    }

}
