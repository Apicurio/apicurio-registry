package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.upgrader.DuplicateVersionOrderUpgrader;
import io.apicurio.registry.storage.impl.sql.repositories.SqlUsageRepository;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@QuarkusTest
public class DefaultRegistryStorageTest extends AbstractRegistryStorageTest {

    /** The schema version whose upgrade scripts add UQ_versions_3. */
    private static final int VERSION_ORDER_DB_VERSION = 113;

    /** The schema version whose upgrade scripts fold schema_usage into schema_usage_daily. */
    private static final int USAGE_ROLLUP_DB_VERSION = 114;

    private static final long DAY_MS = SqlUsageRepository.DAY_MS;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Inject
    SqlStatements sqlStatements;

    /**
     * @see AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    /**
     * The import path writes versionOrder verbatim, so it hands the database a duplicate order
     * without a race. The version name and globalId differ from the existing row, which leaves
     * UQ_versions_3 as the only constraint the insert can break.
     */
    @Test
    public void testDuplicateVersionOrderIsRejected() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactVersionMetaDataDto first = createFirstVersion(groupId, artifactId);
        Assertions.assertEquals(1, first.getVersionOrder());

        ArtifactVersionEntity duplicate = duplicateOf(first, "2");
        RuntimeSqlException e = Assertions.assertThrows(RuntimeSqlException.class,
                () -> storage.importArtifactVersion(duplicate));
        // PostgreSQL folds the unquoted constraint name to lower case; the other dialects keep it.
        Assertions.assertTrue(e.getMessage().toLowerCase(Locale.ROOT).contains("uq_versions_3"),
                "Expected a UQ_versions_3 violation, got: " + e.getMessage());
        Assertions.assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
    }

    /**
     * Seeds the state a registry is in after losing the first-version race: UQ_versions_3 absent
     * and two versions of one artifact sharing a versionOrder. The upgrade to VERSION_ORDER_DB_VERSION
     * must then stop before ADD CONSTRAINT, name both rows and leave db_version one version lower,
     * and it must go through once the operator has removed the duplicate.
     */
    @Test
    public void testVersionOrderUpgradeFailsOnDuplicateVersionOrder() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        ArtifactVersionMetaDataDto first = createFirstVersion(groupId, artifactId);
        String currentVersion = databaseVersion();

        boolean constraintRestored = false;
        execute("ALTER TABLE versions DROP CONSTRAINT UQ_versions_3");
        try {
            storage.importArtifactVersion(duplicateOf(first, "2"));
            execute("UPDATE apicurio SET propValue = '" + (VERSION_ORDER_DB_VERSION - 1)
                    + "' WHERE propName = 'db_version'");

            IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                    this::upgradeToVersionOrderVersion);
            Assertions.assertTrue(e.getMessage().contains("versions share a versionOrder"), e.getMessage());
            Assertions.assertTrue(e.getMessage().contains("groupId=" + groupId + ", artifactId=" + artifactId
                    + ", version=1, versionOrder=1"), e.getMessage());
            Assertions.assertTrue(e.getMessage().contains("groupId=" + groupId + ", artifactId=" + artifactId
                    + ", version=2, versionOrder=1"), e.getMessage());
            Assertions.assertEquals(String.valueOf(VERSION_ORDER_DB_VERSION - 1), databaseVersion());

            storage.deleteArtifactVersion(groupId, artifactId, "2");
            upgradeToVersionOrderVersion();
            constraintRestored = true;
            Assertions.assertEquals(String.valueOf(VERSION_ORDER_DB_VERSION), databaseVersion());
            // The constraint is back, so the same duplicate is rejected again.
            ArtifactVersionEntity next = duplicateOf(first, "3");
            RuntimeSqlException rejected = Assertions.assertThrows(RuntimeSqlException.class,
                    () -> storage.importArtifactVersion(next));
            Assertions.assertTrue(rejected.getMessage().toLowerCase(Locale.ROOT).contains("uq_versions_3"),
                    "Expected a UQ_versions_3 violation, got: " + rejected.getMessage());
            Assertions.assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
        } finally {
            // Put the shared store back the way the other tests expect it.
            if (!constraintRestored) {
                execute("DELETE FROM versions WHERE groupId = '" + groupId + "' AND artifactId = '"
                        + artifactId + "' AND version <> '1'");
                execute("ALTER TABLE versions ADD CONSTRAINT UQ_versions_3 UNIQUE (groupId, artifactId,"
                        + " versionOrder)");
            }
            execute("UPDATE apicurio SET propValue = '" + currentVersion + "' WHERE propName = 'db_version'");
        }
    }

    /**
     * Puts the shared store back to the USAGE_ROLLUP_DB_VERSION - 1 usage layout, a raw schema_usage table
     * with one row per event, and runs the real upgrade scripts. Rows must fold by globalId, contentId,
     * clientId, operation and UTC day of eventTimestamp, with the count and the earliest and latest
     * timestamp of each group, the raw table must be gone, and the new table must take upserts.
     */
    @Test
    public void testUsageRollupUpgradeFoldsRawEventsByKeyAndDay() {
        String currentVersion = databaseVersion();
        String client = TestUtils.generateGroupId("rollup-upgrade");
        long day = 19_000;
        long dayStart = day * DAY_MS;

        Throwable testFailure = null;
        execute("DROP TABLE schema_usage_daily");
        try {
            execute("CREATE TABLE schema_usage (globalId BIGINT NOT NULL, contentId BIGINT NOT NULL, "
                    + "clientId VARCHAR(256) NOT NULL, operation VARCHAR(32) NOT NULL, "
                    + "eventTimestamp BIGINT NOT NULL)");
            insertRawUsage(7, 0, client, "SERIALIZE", dayStart + 5_000);
            insertRawUsage(7, 0, client, "SERIALIZE", dayStart + 1_000);
            insertRawUsage(7, 0, client, "SERIALIZE", dayStart + DAY_MS - 1);
            insertRawUsage(7, 0, client, "SERIALIZE", dayStart + DAY_MS);
            insertRawUsage(7, 0, client, "DESERIALIZE", dayStart + 2_000);
            insertRawUsage(0, 9, client, "DESERIALIZE", dayStart + 3_000);
            execute("UPDATE apicurio SET propValue = '" + (USAGE_ROLLUP_DB_VERSION - 1)
                    + "' WHERE propName = 'db_version'");

            List<String> upgrade = sqlStatements.databaseUpgrade(USAGE_ROLLUP_DB_VERSION - 1,
                    USAGE_ROLLUP_DB_VERSION);
            Assertions.assertTrue(upgrade.stream().noneMatch(statement -> statement.startsWith("UPGRADER:")));
            executeAll(upgrade);

            Assertions.assertEquals(String.valueOf(USAGE_ROLLUP_DB_VERSION), databaseVersion());
            Assertions.assertEquals(List.of(
                    "0|9|DESERIALIZE|" + day + "|1|" + (dayStart + 3_000) + "|" + (dayStart + 3_000),
                    "7|0|DESERIALIZE|" + day + "|1|" + (dayStart + 2_000) + "|" + (dayStart + 2_000),
                    "7|0|SERIALIZE|" + day + "|3|" + (dayStart + 1_000) + "|" + (dayStart + DAY_MS - 1),
                    "7|0|SERIALIZE|" + (day + 1) + "|1|" + (dayStart + DAY_MS) + "|" + (dayStart + DAY_MS)),
                    usageRows(client));
            Assertions.assertThrows(RuntimeSqlException.class, () -> handles.withHandleNoException(handle -> {
                return handle.createQuery("SELECT COUNT(*) FROM schema_usage").mapTo(Integer.class).one();
            }), "the raw table is dropped");

            storage.recordUsageEvent(SchemaUsageEventDto.builder().globalId(7).contentId(0).clientId(client)
                    .operation("SERIALIZE").eventTimestamp(dayStart + 500).build());
            Assertions.assertEquals(
                    "7|0|SERIALIZE|" + day + "|4|" + (dayStart + 500) + "|" + (dayStart + DAY_MS - 1),
                    usageRows(client).get(2), "the upgraded table takes upserts on its primary key");
        } catch (RuntimeException | AssertionError e) {
            testFailure = e;
            throw e;
        } finally {
            restoreUsageTables(currentVersion, testFailure);
        }
    }

    /**
     * Puts the shared store back the way the other tests expect it: db_version first, then both usage tables
     * dropped and the current one recreated with its primary key. Each step runs on its own so that one
     * failing step does not skip the next. A cleanup failure is attached to the test's own failure when there
     * is one, so it never hides it, and fails the test otherwise.
     */
    private void restoreUsageTables(String currentVersion, Throwable testFailure) {
        List<Runnable> steps = List.of(
                () -> execute("UPDATE apicurio SET propValue = '" + currentVersion
                        + "' WHERE propName = 'db_version'"),
                () -> execute("DROP TABLE IF EXISTS schema_usage"),
                () -> execute("DROP TABLE IF EXISTS schema_usage_daily"),
                () -> executeAll(sqlStatements.databaseUpgrade(USAGE_ROLLUP_DB_VERSION - 1, USAGE_ROLLUP_DB_VERSION)
                        .stream().filter(statement -> statement.startsWith("CREATE")
                                || statement.startsWith("ALTER"))
                        .toList()));
        AssertionError cleanupFailure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException e) {
                if (testFailure != null) {
                    testFailure.addSuppressed(e);
                } else if (cleanupFailure == null) {
                    cleanupFailure = new AssertionError("Could not restore the usage tables", e);
                } else {
                    cleanupFailure.addSuppressed(e);
                }
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void insertRawUsage(long globalId, long contentId, String client, String operation,
            long timestamp) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate("INSERT INTO schema_usage (globalId, contentId, clientId, operation, "
                    + "eventTimestamp) VALUES (?, ?, ?, ?, ?)").bind(0, globalId).bind(1, contentId)
                    .bind(2, client).bind(3, operation).bind(4, timestamp).execute();
            return null;
        });
    }

    private List<String> usageRows(String client) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT globalId, contentId, operation, usageDay, fetchCount, "
                    + "firstEventTimestamp, lastEventTimestamp FROM schema_usage_daily WHERE clientId = ? "
                    + "ORDER BY globalId, contentId, operation, usageDay")
                    .bind(0, client)
                    .map(rs -> rs.getLong("globalId") + "|" + rs.getLong("contentId") + "|"
                            + rs.getString("operation") + "|" + rs.getLong("usageDay") + "|"
                            + rs.getLong("fetchCount") + "|" + rs.getLong("firstEventTimestamp") + "|"
                            + rs.getLong("lastEventTimestamp"))
                    .list();
        });
    }

    private void executeAll(List<String> statements) {
        handles.withHandleNoException(handle -> {
            for (String statement : statements) {
                handle.createUpdate(statement).execute();
            }
            return null;
        });
    }

    private ArtifactVersionMetaDataDto createFirstVersion(String groupId, String artifactId) {
        return storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, null, "1",
                ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create(OPENAPI_CONTENT)).build(),
                null, Collections.emptyList(), false, false, null).getValue();
    }

    private ArtifactVersionEntity duplicateOf(ArtifactVersionMetaDataDto existing, String version) {
        ArtifactVersionEntity duplicate = new ArtifactVersionEntity();
        duplicate.globalId = storage.nextGlobalId();
        duplicate.groupId = existing.getGroupId();
        duplicate.artifactId = existing.getArtifactId();
        duplicate.version = version;
        duplicate.versionOrder = existing.getVersionOrder();
        duplicate.state = VersionState.ENABLED;
        duplicate.contentId = existing.getContentId();
        // The entity's epoch default (0L) falls below MySQL TIMESTAMP's 1970-01-01 00:00:01 floor,
        // so MySQL would reject the insert for the datetime before UQ_versions_3 is evaluated.
        duplicate.createdOn = System.currentTimeMillis();
        duplicate.modifiedOn = System.currentTimeMillis();
        return duplicate;
    }

    /**
     * Runs the real VERSION_ORDER_DB_VERSION scripts the way
     * AbstractSqlRegistryStorage.upgradeDatabaseRaw does.
     */
    private void upgradeToVersionOrderVersion() {
        handles.withHandleNoException(handle -> {
            for (String statement : sqlStatements.databaseUpgrade(VERSION_ORDER_DB_VERSION - 1,
                    VERSION_ORDER_DB_VERSION)) {
                if (statement.startsWith("UPGRADER:")) {
                    String className = statement.substring("UPGRADER:".length()).trim();
                    Assertions.assertEquals(DuplicateVersionOrderUpgrader.class.getName(), className);
                    ((IDbUpgrader) Class.forName(className).getConstructor().newInstance()).upgrade(handle);
                } else {
                    handle.createUpdate(statement).execute();
                }
            }
            return null;
        });
    }

    private void execute(String sql) {
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sql).execute();
            return null;
        });
    }

    private String databaseVersion() {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT propValue FROM apicurio WHERE propName = 'db_version'")
                    .mapTo(String.class).one();
        });
    }

}
