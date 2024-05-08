package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.kafka.ProducerActions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

/*
 * Periodically triggers Kafkasql snapshots
 *
 */
@ApplicationScoped
public class KafkaSqlSnapshotManager {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    @Named("KafkaSqlSnapshotsProducer")
    ProducerActions<String, String> producer;

    @Inject
    KafkaSqlConfiguration kafkaSqlConfiguration;

    public void triggerSnapshotCreation() {
        String snapshotId = UUID.randomUUID().toString();
        Path path = Path.of(kafkaSqlConfiguration.snapshotLocation(), snapshotId + ".sql");
        String snapshotPath = storage.triggerSnapshotCreation(path.toString());
        sendSnapshotMessage(snapshotId, snapshotPath);
    }

    public void sendSnapshotMessage(String snapshotId, String snapshotLocation) {
        ProducerRecord<String, String> record = new ProducerRecord<>(kafkaSqlConfiguration.snapshotsTopic(), 0, snapshotId, snapshotLocation,
                Collections.emptyList());
        producer.apply(record);
    }
}
