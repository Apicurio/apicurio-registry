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
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.kafka.snapshot.StorageSnapshot;
import io.apicurio.registry.kafka.snapshot.StorageSnapshotSerde;
import io.apicurio.registry.utils.kafka.ConsumerActions;
import io.apicurio.registry.utils.kafka.ConsumerContainer;
import io.apicurio.registry.utils.kafka.Oneof2;
import io.apicurio.registry.utils.kafka.Seek;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Ales Justin
 */
public class RegistryConsumerContainer extends ConsumerContainer<Cmmn.UUID, Str.StorageValue> implements ConsumerActions.DynamicAssignment<Cmmn.UUID, Str.StorageValue> {

    private static final long SNAPSHOTS_POLL_TIMEOUT = 15_000; // 15 seconds should be enough

    private KafkaRegistryStorageHandle handle;
    private Properties snapshotProperties;

    public RegistryConsumerContainer(
        Properties consumerProperties,
        Deserializer<Cmmn.UUID> keyDeserializer,
        Deserializer<Str.StorageValue> valueDeserializer,
        KafkaRegistryStorageHandle handle,
        Properties snapshotProperties
    ) {
        super(consumerProperties, keyDeserializer, valueDeserializer, Oneof2.first(handle::consumeStorageValue));
        this.handle = handle;
        this.snapshotProperties = snapshotProperties;
    }

    @Override
    public void start() {
        super.start();

        // read from Snapshot
        StorageSnapshot snapshot = loadSnapshot();
        Seek.Offset offset;
        if (snapshot != null) {
            handle.loadSnapshot(snapshot);
            offset = (snapshot.getOffset() > 0) ? Seek.TO_ABSOLUTE.offset(snapshot.getOffset() + 1) : Seek.FROM_BEGINNING.offset(0);
        } else {
            offset = Seek.FROM_BEGINNING.offset(0);
        }

        addTopicPartition(new TopicPartition(handle.registryTopic(), 0), offset);

        handle.start();
    }

    @Override
    public void stop() {
        handle.stop();
        removeTopicParition(new TopicPartition(handle.registryTopic(), 0));
        super.stop();
    }

    // handle / load snapshot

    private StorageSnapshot loadSnapshot() {
        TopicPartition snapshotsTp = new TopicPartition(handle.snapshotTopic(), 0);
        try (org.apache.kafka.clients.consumer.Consumer<Long, StorageSnapshot> consumer = new KafkaConsumer<>(
            snapshotProperties,
            Serdes.Long().deserializer(),
            new StorageSnapshotSerde())
        ) {
            consumer.assign(Collections.singleton(snapshotsTp));
            long offset = consumer.endOffsets(Collections.singleton(snapshotsTp)).get(snapshotsTp);
            if (offset == 0L) {
                // no snapshots written yet -> return null to signal
                return null;
            } else {
                // seek to 1 before last snapshot
                consumer.seek(snapshotsTp, offset - 1);
                // read the snapshot
                Optional<? extends ConsumerRecord<?, StorageSnapshot>> rec =
                    consumer.poll(Duration.ofMillis(SNAPSHOTS_POLL_TIMEOUT))
                            .records(snapshotsTp)
                            .stream()
                            .reduce((rec1, rec2) -> rec1.offset() > rec2.offset() ? rec1 : rec2);
                return rec.orElseThrow(() -> new IllegalStateException("Couldn't read last snapshot in " + SNAPSHOTS_POLL_TIMEOUT + " ms"))
                          .value();
            }
        }

    }
}
