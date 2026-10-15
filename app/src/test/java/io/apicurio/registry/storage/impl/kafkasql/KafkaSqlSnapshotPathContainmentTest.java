package io.apicurio.registry.storage.impl.kafkasql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the KafkaSQL snapshot-path containment (defense-in-depth). A snapshot path is
 * read off the internal {@code snapshots} topic and drives an H2 {@code RUNSCRIPT} at startup, so
 * a value pointing outside the configured snapshot store must be rejected.
 */
class KafkaSqlSnapshotPathContainmentTest {

    @Test
    void acceptsPathInsideAbsoluteStore() {
        assertTrue(KafkaSqlRegistryStorage.isWithinSnapshotStore(
                "/data/snapshots", "/data/snapshots/abc.sql.gz"));
    }

    @Test
    void acceptsPathInsideDefaultRelativeStore() {
        // Default store location is "./"; the registry writes "<uuid>.sql.gz" relative to it.
        assertTrue(KafkaSqlRegistryStorage.isWithinSnapshotStore("./", "abc.sql.gz"));
        assertTrue(KafkaSqlRegistryStorage.isWithinSnapshotStore("./", "./abc.sql.gz"));
    }

    @Test
    void rejectsAbsolutePathOutsideStore() {
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore(
                "/data/snapshots", "/etc/passwd"));
    }

    @Test
    void rejectsParentTraversalOutOfStore() {
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore(
                "/data/snapshots", "/data/snapshots/../../etc/passwd"));
    }

    @Test
    void rejectsRelativeTraversalOutOfDefaultStore() {
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore("./", "../abc.sql.gz"));
    }

    @Test
    void rejectsBlankOrNull() {
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore(null, "abc.sql.gz"));
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore("/data/snapshots", null));
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore("", "abc.sql.gz"));
        assertFalse(KafkaSqlRegistryStorage.isWithinSnapshotStore("/data/snapshots", "  "));
    }
}
