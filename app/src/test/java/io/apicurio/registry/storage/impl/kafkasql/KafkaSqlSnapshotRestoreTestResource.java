package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateGroup1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateSnapshot1Message;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.MESSAGE_TYPE_HEADER;
import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter.REQUEST_ID_HEADER;

/**
 * Starts Kafka and, before the registry boots, seeds the state a previous registry instance would
 * have left behind after taking a snapshot: a gzipped H2 snapshot file in the snapshot store, a
 * journal with one record before the snapshot marker, the marker itself, and one record after it,
 * and a two-partition snapshots topic. Partition 0 holds the valid snapshot record; partition 1
 * holds a newer record whose path lies outside the snapshot store, which the registry must skip.
 * <p>
 * By default the snapshot record has no journal position, as written before the position was
 * recorded. The {@value #SCENARIO} init argument selects a record that has one:
 * <ul>
 * <li>{@value #SEEK_TO_MARKER}: the journal starts with an earlier copy of the same marker, and the
 * position points at the real marker. Reading the journal from the beginning stops at the copy and
 * applies {@link #BEFORE_MARKER_GROUP}; starting at the position does not.</li>
 * <li>{@value #POSITION_PAST_MARKER}: the position points at the record after the marker, which the
 * registry must detect and fall back to reading the journal from the beginning.</li>
 * <li>{@value #POSITION_AT_JOURNAL_END}: the position is the journal end offset once the registry has
 * appended its bootstrap record, which the registry must reject and read the journal from the
 * beginning instead.</li>
 * </ul>
 */
public class KafkaSqlSnapshotRestoreTestResource extends KafkaTestContainerManager {

    /** Exists only in the snapshot file: present after startup only if the snapshot was restored. */
    static final String SNAPSHOT_ONLY_GROUP = "snapshot-only-group";
    /** Journaled before the marker: the snapshot already covers it, so it must not be replayed. */
    static final String BEFORE_MARKER_GROUP = "before-marker-group";
    /** Journaled after the marker: it must be applied on top of the restored snapshot. */
    static final String AFTER_MARKER_GROUP = "after-marker-group";

    static final String SCENARIO = "scenario";
    static final String SEEK_TO_MARKER = "seek-to-marker";
    static final String POSITION_PAST_MARKER = "position-past-marker";
    static final String POSITION_AT_JOURNAL_END = "position-at-journal-end";

    private static final long SEED_TIMEOUT_SECONDS = 30;

    private String scenario;

    @Override
    public void init(Map<String, String> initArgs) {
        scenario = initArgs.get(SCENARIO);
    }

    /** Holds the snapshot store and, next to it, the file the rejected snapshot record points at. */
    private Path tempRoot;

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>(super.start());
        String bootstrapServers = config.get("apicurio.kafkasql.bootstrap.servers");
        if (bootstrapServers == null) {
            throw new IllegalStateException("The snapshot restore scenario needs its own pre-seeded broker "
                    + "and cannot run with -Dcluster.tests=true");
        }

        // super.start() gave this resource its own broker, so fixed topic names cannot collide.
        String journalTopic = "snapshot-restore-journal";
        String snapshotsTopic = "snapshot-restore-snapshots";
        String snapshotId = UUID.randomUUID().toString();
        Path snapshotStore;
        try {
            tempRoot = Files.createTempDirectory("snapshot-restore");
            snapshotStore = Files.createDirectory(tempRoot.resolve("store"));
            Path snapshotFile = snapshotStore
                    .resolve(snapshotId + SqlStatements.COMPRESSED_SNAPSHOT_EXTENSION);
            writeSnapshot(snapshotFile);
            // A real file, so only the store containment check can reject it: if that check is
            // bypassed, the registry picks this newer record and fails to run a non-gzip script.
            Path outsideStore = Files.writeString(
                    tempRoot.resolve(UUID.randomUUID() + SqlStatements.COMPRESSED_SNAPSHOT_EXTENSION),
                    "not a snapshot");
            seedTopics(bootstrapServers, journalTopic, snapshotsTopic, snapshotId, snapshotFile, outsideStore,
                    scenario);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not seed the snapshot restore scenario", e);
        }
        config.put("apicurio.storage.snapshot.location", snapshotStore.toString());
        config.put("apicurio.kafkasql.topic", journalTopic);
        config.put("apicurio.kafkasql.snapshots.topic", snapshotsTopic);
        return config;
    }

    @Override
    public void stop() {
        try {
            if (tempRoot != null) {
                try (Stream<Path> files = Files.walk(tempRoot)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete " + tempRoot, e);
        } finally {
            super.stop();
        }
    }

    /**
     * Builds the snapshot the way the registry does: the current H2 schema, one group row, then the
     * same SCRIPT statement the registry uses to dump its database.
     */
    private static void writeSnapshot(Path snapshotFile) throws SQLException {
        H2SqlStatements statements = new H2SqlStatements();
        String location = snapshotFile.toString();
        String url = "jdbc:h2:mem:snapshot-restore-seed-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "sa");
                Statement ddl = connection.createStatement();
                PreparedStatement script = connection.prepareStatement(statements.createDataSnapshot(location))) {
            for (String statement : statements.databaseInitialization()) {
                ddl.execute(statement);
            }
            // Prepared only now: H2 validates the table when the statement is prepared.
            try (PreparedStatement insert = connection
                    .prepareStatement("INSERT INTO groups (groupId, createdOn) VALUES (?, CURRENT_TIMESTAMP)")) {
                insert.setString(1, SNAPSHOT_ONLY_GROUP);
                insert.executeUpdate();
            }
            script.setString(1, location);
            script.execute();
        }
    }

    private static void seedTopics(String bootstrapServers, String journalTopic, String snapshotsTopic,
            String snapshotId, Path snapshotFile, Path outsideStore, String scenario) throws Exception {
        Map<String, String> retainForever = Map.of(TopicConfig.CLEANUP_POLICY_CONFIG,
                TopicConfig.CLEANUP_POLICY_DELETE, TopicConfig.RETENTION_MS_CONFIG, "-1",
                TopicConfig.RETENTION_BYTES_CONFIG, "-1");
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            admin.createTopics(List.of(new NewTopic(journalTopic, 1, (short) 1).configs(retainForever),
                    new NewTopic(snapshotsTopic, 2, (short) 1).configs(retainForever))).all()
                    .get(SEED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        Map<String, Object> producerConfig = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.ACKS_CONFIG, "all");
        KafkaSqlMessage marker = new CreateSnapshot1Message(snapshotFile.toString(), snapshotId);
        List<KafkaSqlMessage> messages = new ArrayList<>();
        if (SEEK_TO_MARKER.equals(scenario)) {
            messages.add(marker);
        }
        messages.addAll(List.of(createGroup(BEFORE_MARKER_GROUP), marker, createGroup(AFTER_MARKER_GROUP)));
        // Offsets of the journal records, in the order above (one partition, one send per record).
        List<Long> offsets = new ArrayList<>();
        try (KafkaProducer<KafkaSqlMessageKey, KafkaSqlMessage> journal = new KafkaProducer<>(producerConfig,
                new KafkaSqlKeySerializer(), new KafkaSqlValueSerializer())) {
            for (KafkaSqlMessage message : messages) {
                offsets.add(journal.send(journalRecord(journalTopic, message))
                        .get(SEED_TIMEOUT_SECONDS, TimeUnit.SECONDS).offset());
            }
        }
        List<Header> markerPosition = List.of();
        if (scenario != null) {
            int markerIndex = messages.lastIndexOf(marker);
            long position = switch (scenario) {
                case POSITION_PAST_MARKER -> offsets.get(markerIndex + 1);
                // startConsumerThread appends one bootstrap record (submitBootstrap) before it
                // subscribes, so the journal then ends two past the last seeded offset. If that ever
                // changes, this position stops being the end and the scenario tests something else.
                case POSITION_AT_JOURNAL_END -> offsets.get(offsets.size() - 1) + 2;
                case SEEK_TO_MARKER -> offsets.get(markerIndex);
                default -> throw new IllegalArgumentException("Unknown scenario " + scenario);
            };
            markerPosition = new JournalPosition(0, position).toHeaders();
        }
        // The rejected record is sent second so its timestamp is newer: the registry has to skip it
        // rather than merely prefer the older valid one.
        try (KafkaProducer<String, String> snapshots = new KafkaProducer<>(producerConfig,
                new StringSerializer(), new StringSerializer())) {
            snapshots.send(new ProducerRecord<>(snapshotsTopic, 0, snapshotId, snapshotFile.toString(),
                    markerPosition))
                    .get(SEED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            snapshots.send(new ProducerRecord<>(snapshotsTopic, 1, UUID.randomUUID().toString(),
                    outsideStore.toString())).get(SEED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static CreateGroup1Message createGroup(String groupId) {
        return new CreateGroup1Message(GroupMetaDataDto.builder().groupId(groupId)
                .createdOn(System.currentTimeMillis()).build());
    }

    /** Same key and headers the registry's KafkaSqlSubmitter writes; the partition is left to the producer. */
    private static ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> journalRecord(String topic,
            KafkaSqlMessage message) {
        KafkaSqlMessageKey key = message.getKey();
        return new ProducerRecord<>(topic, null, key, message, List.of(
                new RecordHeader(REQUEST_ID_HEADER,
                        UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)),
                new RecordHeader(MESSAGE_TYPE_HEADER, key.getMessageType().getBytes(StandardCharsets.UTF_8))));
    }
}
