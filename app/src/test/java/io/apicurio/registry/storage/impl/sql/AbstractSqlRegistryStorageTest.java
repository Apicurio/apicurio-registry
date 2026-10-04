package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class AbstractSqlRegistryStorageTest {

    @Test
    void testIsDatabaseCurrentRawThrowsWhenSchemaIsNewer() {
        TestSqlRegistryStorage storage = new TestSqlRegistryStorage();
        storage.log = LoggerFactory.getLogger(AbstractSqlRegistryStorageTest.class);
        int newerVersion = AbstractSqlRegistryStorage.getDbVersion() + 1;
        storage.mockVersion = newerVersion;

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> {
            storage.isDatabaseCurrentRaw(null);
        });

        Assertions.assertTrue(ex.getMessage().contains(String.valueOf(newerVersion)));
        Assertions.assertTrue(ex.getMessage().contains(String.valueOf(AbstractSqlRegistryStorage.getDbVersion())));
        Assertions.assertTrue(ex.getMessage().contains("Starting an older version of the registry against a newer database schema is not supported"));
    }

    @Test
    void testIsDatabaseCurrentRawReturnsTrueWhenCurrent() {
        TestSqlRegistryStorage storage = new TestSqlRegistryStorage();
        storage.log = LoggerFactory.getLogger(AbstractSqlRegistryStorageTest.class);
        storage.mockVersion = AbstractSqlRegistryStorage.getDbVersion();

        Assertions.assertTrue(storage.isDatabaseCurrentRaw(null));
    }

    @Test
    void testIsDatabaseCurrentRawReturnsFalseWhenOlder() {
        TestSqlRegistryStorage storage = new TestSqlRegistryStorage();
        storage.log = LoggerFactory.getLogger(AbstractSqlRegistryStorageTest.class);
        storage.mockVersion = 100;

        Assertions.assertFalse(storage.isDatabaseCurrentRaw(null));
    }

    @Test
    void testIsDatabaseCurrentRawThrowsWhenLegacyV2() {
        TestSqlRegistryStorage storage = new TestSqlRegistryStorage();
        storage.log = LoggerFactory.getLogger(AbstractSqlRegistryStorageTest.class);
        storage.mockVersion = 99;

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> {
            storage.isDatabaseCurrentRaw(null);
        });
        Assertions.assertTrue(ex.getMessage().contains("Detected legacy 2.x database"));
    }

    @Test
    void testUpgradeDatabaseRawThrowsWhenFromVersionIsNewer() {
        TestSqlRegistryStorage storage = new TestSqlRegistryStorage();
        storage.log = LoggerFactory.getLogger(AbstractSqlRegistryStorageTest.class);
        int newerVersion = AbstractSqlRegistryStorage.getDbVersion() + 1;
        storage.mockVersion = newerVersion;

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> {
            storage.upgradeDatabaseRaw(null);
        });

        Assertions.assertTrue(ex.getMessage().contains(String.valueOf(newerVersion)));
        Assertions.assertTrue(ex.getMessage().contains(String.valueOf(AbstractSqlRegistryStorage.getDbVersion())));
        Assertions.assertTrue(ex.getMessage().contains("Starting an older version of the registry against a newer database schema is not supported"));
    }

    private static class TestSqlRegistryStorage extends AbstractSqlRegistryStorage {
        int mockVersion;

        @Override
        public void initialize() {
        }

        @Override
        protected int getDatabaseVersionRaw(Handle handle) {
            return mockVersion;
        }
    }
}
