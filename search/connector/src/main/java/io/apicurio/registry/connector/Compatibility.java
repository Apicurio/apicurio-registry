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

package io.apicurio.registry.connector;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.connect.connector.policy.ConnectorClientConfigOverridePolicy;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.runtime.Worker;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.runtime.distributed.DistributedConfig;
import org.apache.kafka.connect.runtime.distributed.DistributedHerder;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.storage.ConfigBackingStore;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.apache.kafka.connect.storage.StatusBackingStore;

import java.lang.reflect.Constructor;

/**
 * Compatibility utilities.
 * It is assumed that the code is compiled with kafka-connect 2.4 and its resulting jar may be used
 * not only with kafka-connect 2.4 but also with kafka-connect 2.2, 2.3 which are not binary compatible
 * with kafka-connect 2.4 in some classes.
 */
class Compatibility {
    private static Class<?> CLS_CONNECTOR_CLIENT_CONFIG_OVERRIDE_POLICY;
    private static Constructor<?> CTR_WORKER_22;
    private static Constructor<?> CTR_DISTRIBUTED_HERDER_22;

    static {
        try {
            // for 2.3, 2.4 - ConnectorClientConfigOverridePolicy is available
            CLS_CONNECTOR_CLIENT_CONFIG_OVERRIDE_POLICY = Class.forName("org.apache.kafka.connect.connector.policy.ConnectorClientConfigOverridePolicy", true, Compatibility.class.getClassLoader());
        } catch (Throwable t) {
            // for 2.2 - not available
            CLS_CONNECTOR_CLIENT_CONFIG_OVERRIDE_POLICY = null;
        }
        try {
            // for 2.2, 2.3 - get the consturctor from the old Worker
            CTR_WORKER_22 = Worker.class.getConstructor(String.class, Time.class, Plugins.class, WorkerConfig.class, OffsetBackingStore.class);
        } catch (Throwable t) {
            // for 2.4 - use the current constructor
            CTR_WORKER_22 = null;
        }
        try {
            // for 2.2, 2.3 - get the constructor from the old DistributedHerder
            CTR_DISTRIBUTED_HERDER_22 = DistributedHerder.class.getConstructor(DistributedConfig.class, Time.class, Worker.class, String.class, StatusBackingStore.class, ConfigBackingStore.class, String.class);
        } catch (Throwable t) {
            // for 2.4 - use the current constructor
            CTR_DISTRIBUTED_HERDER_22 = null;
        }
    }

    static Object createConnectorClientConfigOverridePolicy(Plugins plugins, AbstractConfig config) throws ConnectException {
        if (CLS_CONNECTOR_CLIENT_CONFIG_OVERRIDE_POLICY != null) {
            return plugins.newPlugin(
                    config.getString(WorkerConfig.CONNECTOR_CLIENT_POLICY_CLASS_CONFIG),
                    config, CLS_CONNECTOR_CLIENT_CONFIG_OVERRIDE_POLICY);
        }
        return null;
    }

    static Worker createWorker(String workerId,
                               Time time,
                               Plugins plugins,
                               WorkerConfig config,
                               OffsetBackingStore offsetBackingStore,
                               Object connectorClientConfigOverridePolicy) throws ConnectException {

        if (CTR_WORKER_22 == null) {
            return new Worker(workerId, time, plugins, config, offsetBackingStore, (ConnectorClientConfigOverridePolicy)connectorClientConfigOverridePolicy);
        }
        try {
            return (Worker)CTR_WORKER_22.newInstance(workerId, time, plugins, config, offsetBackingStore);
        } catch (Throwable t) {
            throw new ConnectException(t);
        }
    }

    static DistributedHerder createDistributedHerder(DistributedConfig config,
                                                     Time time,
                                                     Worker worker,
                                                     String kafkaClusterId,
                                                     StatusBackingStore statusBackingStore,
                                                     ConfigBackingStore configBackingStore,
                                                     String restUrl,
                                                     Object connectorClientConfigOverridePolicy) throws ConnectException {

        if (CTR_DISTRIBUTED_HERDER_22 == null) {
            return new DistributedHerder(config, time, worker, kafkaClusterId, statusBackingStore, configBackingStore, restUrl, (ConnectorClientConfigOverridePolicy)connectorClientConfigOverridePolicy);
        }
        try {
            return (DistributedHerder)CTR_DISTRIBUTED_HERDER_22.newInstance(config, time, worker, kafkaClusterId, statusBackingStore, configBackingStore, restUrl);
        } catch (Throwable t) {
            throw new ConnectException(t);
        }
    }
}
