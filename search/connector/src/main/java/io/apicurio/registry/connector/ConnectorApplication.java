/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.utils.RegistryProperties;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.connect.runtime.Connect;
import org.apache.kafka.connect.runtime.Worker;
import org.apache.kafka.connect.runtime.WorkerConfigTransformer;
import org.apache.kafka.connect.runtime.distributed.DistributedConfig;
import org.apache.kafka.connect.runtime.distributed.DistributedHerder;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.runtime.rest.RestServer;
import org.apache.kafka.connect.storage.ConfigBackingStore;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.KafkaConfigBackingStore;
import org.apache.kafka.connect.storage.KafkaOffsetBackingStore;
import org.apache.kafka.connect.storage.KafkaStatusBackingStore;
import org.apache.kafka.connect.storage.StatusBackingStore;
import org.apache.kafka.connect.util.ConnectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class ConnectorApplication {
    private static final Logger log = LoggerFactory.getLogger(ConnectorApplication.class);

    private final Map<String, String> workerProperties;

    private final Time time = Time.SYSTEM;
    private final long initStart = time.hiResClockMs();

    private Connect connect;

    public ConnectorApplication(@RegistryProperties Properties properties) {
        workerProperties = Utils.propsToStringMap(properties);
    }

    public void start() {
        connect = startConnect(workerProperties);
    }

    public void stop() {
        if (connect != null) {
            connect.stop();
        }
    }

    // Hack around TCCL switch -- keep in-sync with Kafka's ConnectDistributed::startConnect
    private Connect startConnect(Map<String, String> workerProps) {
        log.info("Scanning for plugin classes. This might take a moment ...");
        Plugins plugins = new Plugins(workerProps);
        // ignore this TCCL switch plugins.compareAndSwapWithDelegatingLoader();
        DistributedConfig config = new DistributedConfig(workerProps);

        String kafkaClusterId = ConnectUtils.lookupKafkaClusterId(config);
        log.debug("Kafka cluster ID: {}", kafkaClusterId);

        RestServer rest = new RestServer(config);
        rest.initializeServer();

        URI advertisedUrl = rest.advertisedUrl();
        String workerId = advertisedUrl.getHost() + ":" + advertisedUrl.getPort();

        KafkaOffsetBackingStore offsetBackingStore = new KafkaOffsetBackingStore();
        offsetBackingStore.configure(config);

        Worker worker = new Worker(workerId, time, plugins, config, offsetBackingStore);
        WorkerConfigTransformer configTransformer = worker.configTransformer();

        Converter internalValueConverter = worker.getInternalValueConverter();
        StatusBackingStore statusBackingStore = new KafkaStatusBackingStore(time, internalValueConverter);
        statusBackingStore.configure(config);

        ConfigBackingStore configBackingStore = new KafkaConfigBackingStore(
            internalValueConverter,
            config,
            configTransformer);

        DistributedHerder herder = new DistributedHerder(config, time, worker,
                                                         kafkaClusterId, statusBackingStore, configBackingStore,
                                                         advertisedUrl.toString());

        final Connect connect = new Connect(herder, rest);
        log.info("Kafka Connect distributed worker initialization took {}ms", time.hiResClockMs() - initStart);
        try {
            connect.start();
        } catch (Exception e) {
            log.error("Failed to start Connect", e);
            connect.stop();
            throw new IllegalStateException(e);
        }

        return connect;
    }

}
