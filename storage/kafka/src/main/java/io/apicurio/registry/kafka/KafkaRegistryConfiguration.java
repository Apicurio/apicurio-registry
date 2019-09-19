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

import io.apicurio.registry.common.proto.Cmmn;
import io.apicurio.registry.kafka.snapshot.StorageSnapshot;
import io.apicurio.registry.kafka.snapshot.StorageSnapshotSerde;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ConsumerActions;
import io.apicurio.registry.utils.kafka.KafkaProperties;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.apache.kafka.common.serialization.Serdes;

import static io.apicurio.registry.utils.kafka.KafkaUtil.applyGroupId;

import java.util.Properties;
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

    @Produces
    public Properties properties(InjectionPoint ip) {
        KafkaProperties kp = ip.getAnnotated().getAnnotation(KafkaProperties.class);
        return KafkaUtil.properties(kp);
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
    public ProducerActions<Cmmn.UUID, Str.StorageValue> storageProducer(
        @KafkaProperties("registry.kafka.storage-producer.") Properties properties
    ) {
        return new AsyncProducer<>(
            properties,
            ProtoSerde.parsedWith(Cmmn.UUID.parser()),
            ProtoSerde.parsedWith(Str.StorageValue.parser())
        );
    }

    public void stopStorageProducer(@Disposes ProducerActions<Cmmn.UUID, Str.StorageValue> producer) throws Exception {
        producer.close();
    }

    @Produces
    @ApplicationScoped
    public ConsumerActions.DynamicAssignment<Cmmn.UUID, Str.StorageValue> registryContainer(
        @KafkaProperties("registry.kafka.storage-consumer.") Properties registryProperties,
        @KafkaProperties("registry.kafka.snapshot-consumer.") Properties snapshotProperties,
        KafkaRegistryStorageHandle handle
    ) {
        // persistent unique group id
        applyGroupId("storage", registryProperties);
        applyGroupId("snapshot", snapshotProperties);

        return new RegistryConsumerContainer(
            registryProperties,
            ProtoSerde.parsedWith(Cmmn.UUID.parser()),
            ProtoSerde.parsedWith(Str.StorageValue.parser()),
            handle,
            snapshotProperties
        );
    }

    public void init(@Observes StartupEvent event, ConsumerActions.DynamicAssignment<Cmmn.UUID, Str.StorageValue> container) {
        container.start();
    }

    public void destroy(@Observes ShutdownEvent event, ConsumerActions.DynamicAssignment<Cmmn.UUID, Str.StorageValue> container) {
        container.stop();
    }
}
