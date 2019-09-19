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

package io.apicurio.registry.kafka;

import io.apicurio.registry.kafka.proto.Reg;
import io.apicurio.registry.kafka.snapshot.StorageSnapshot;
import io.apicurio.registry.kafka.snapshot.StorageSnapshotSerde;
import io.apicurio.registry.kafka.utils.AsyncProducer;
import io.apicurio.registry.kafka.utils.ConsumerActions;
import io.apicurio.registry.kafka.utils.KafkaProperties;
import io.apicurio.registry.kafka.utils.ProducerActions;
import io.apicurio.registry.kafka.utils.ProtoSerde;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaRegistryConfiguration.class);

    @Produces
    public Properties properties(InjectionPoint ip) {
        KafkaProperties kp = ip.getAnnotated().getAnnotation(KafkaProperties.class);
        String prefix = (kp != null ? kp.value() : "");
        Config config = ConfigProvider.getConfig();
        Optional<String> po = config.getOptionalValue("quarkus.profile", String.class);
        if (po.isPresent()) {
            String profile = po.get();
            if (profile.length() > 0) {
                prefix = "%" + profile + "." + prefix;
            }
        }

        Properties properties = new Properties();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                properties.put(key.substring(prefix.length()), config.getValue(key, String.class));
            }
        }
        return properties;
    }

    @Produces
    @ApplicationScoped
    public ProducerActions<Long, StorageSnapshot> snapshotProducer(
        @KafkaProperties("registry.kafka.snapshot-producer.") Properties properties
    ) {
        return new AsyncProducer<>(
            properties,
            Serdes.Long().serializer(),
            new StorageSnapshotSerde()
        );
    }

    public void stopSnapshotProducer(@Disposes ProducerActions<Long, StorageSnapshot> producer) throws Exception {
        producer.close();
    }

    @Produces
    @ApplicationScoped
    public ProducerActions<Reg.UUID, Reg.RegistryValue> registryProducer(
        @KafkaProperties("registry.kafka.registry-producer.") Properties properties
    ) {
        return new AsyncProducer<>(
            properties,
            ProtoSerde.parsedWith(Reg.UUID.parser()),
            ProtoSerde.parsedWith(Reg.RegistryValue.parser())
        );
    }

    public void stopRegistryProducer(@Disposes ProducerActions<Reg.UUID, Reg.RegistryValue> producer) throws Exception {
        producer.close();
    }

    @Produces
    @ApplicationScoped
    public ConsumerActions.DynamicAssignment<Reg.UUID, Reg.RegistryValue> registryContainer(
        @KafkaProperties("registry.kafka.registry-consumer.") Properties registryProperties,
        @KafkaProperties("registry.kafka.snapshot-consumer.") Properties snapshotProperties,
        KafkaRegistryStorageHandle handle
    ) {
        // persistent unique group id
        applyGroupId("registry", registryProperties);
        applyGroupId("snapshot", snapshotProperties);

        return new RegistryConsumerContainer(
            registryProperties,
            ProtoSerde.parsedWith(Reg.UUID.parser()),
            ProtoSerde.parsedWith(Reg.RegistryValue.parser()),
            handle,
            snapshotProperties
        );
    }

    private static void applyGroupId(String type, Properties properties) {
        String groupId = properties.getProperty("group.id");
        if (groupId == null) {
            log.warn("No group.id set for " + type + " properties, creating one ... DEV env only!!");
            properties.put("group.id", UUID.randomUUID().toString());
        }
    }

    public void init(@Observes StartupEvent event, ConsumerActions.DynamicAssignment<Reg.UUID, Reg.RegistryValue> container) {
        container.start();
    }

    public void destroy(@Observes ShutdownEvent event, ConsumerActions.DynamicAssignment<Reg.UUID, Reg.RegistryValue> container) {
        container.stop();
    }
}
