package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.repositories.SqlSequenceRepository;
import io.apicurio.registry.storage.impl.sql.upgrader.DuplicateVersionOrderUpgrader;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class DefaultRegistryStorageTest extends AbstractRegistryStorageTest {

    /** The schema version whose upgrade scripts add UQ_versions_3. */
    private static final int VERSION_ORDER_DB_VERSION = 113;

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
     * The newest version with a given content is the one with the highest versionOrder. globalId
     * is not a creation-order signal (imports preserve arbitrary IDs, and native sequences do not
     * follow commit order), so the newest version is given the lower globalId here.
     */
    @Test
    public void testVersionByContentPrefersHighestVersionOrder() {
        // A throwaway artifact provides a contentId for OPENAPI_CONTENT.
        ArtifactVersionMetaDataDto source = createFirstVersion(TestUtils.generateGroupId(),
                TestUtils.generateArtifactId());
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        long now = System.currentTimeMillis();
        storage.importArtifact(ArtifactEntity.builder().groupId(groupId).artifactId(artifactId)
                .artifactType(ArtifactType.OPENAPI).owner("test").createdOn(now).modifiedBy("test")
                .modifiedOn(now).build());

        long lowerGlobalId = storage.nextGlobalId();
        long higherGlobalId = storage.nextGlobalId();
        ArtifactVersionEntity oldest = duplicateOf(source, "1");
        oldest.groupId = groupId;
        oldest.artifactId = artifactId;
        oldest.versionOrder = 1;
        oldest.globalId = higherGlobalId;
        storage.importArtifactVersion(oldest);
        ArtifactVersionEntity newest = duplicateOf(source, "2");
        newest.groupId = groupId;
        newest.artifactId = artifactId;
        newest.versionOrder = 2;
        newest.globalId = lowerGlobalId;
        storage.importArtifactVersion(newest);

        TypedContent content = TypedContent.create(ContentHandle.create(OPENAPI_CONTENT),
                ContentTypes.APPLICATION_JSON);
        ArtifactVersionMetaDataDto byHash = storage.getArtifactVersionMetaDataByContent(groupId,
                artifactId, false, content, Collections.emptyList());
        ArtifactVersionMetaDataDto byCanonicalHash = storage.getArtifactVersionMetaDataByContent(groupId,
                artifactId, true, content, Collections.emptyList());

        Assertions.assertEquals("2", byHash.getVersion());
        Assertions.assertEquals(lowerGlobalId, byHash.getGlobalId());
        Assertions.assertEquals("2", byCanonicalHash.getVersion());
        Assertions.assertEquals(lowerGlobalId, byCanonicalHash.getGlobalId());
    }

    private SqlSequenceRepository sequences() {
        return new SqlSequenceRepository(handles, sqlStatements,
                LoggerFactory.getLogger(DefaultRegistryStorageTest.class));
    }

    /**
     * Allocating an ID must not wait for another transaction that allocated an ID and has not
     * committed yet. With the old sequences-table upsert the second caller blocks on the row lock.
     */
    @Test
    public void testIdAllocationDoesNotWaitForOpenTransactions() throws Exception {
        SqlSequenceRepository sequences = sequences();
        CountDownLatch allocated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> holder = executor.submit(() -> handles.withHandleNoException(handle -> {
                long id = sequences.nextGlobalIdRaw(handle);
                allocated.countDown();
                Assertions.assertTrue(release.await(30, TimeUnit.SECONDS));
                return id;
            }));
            Assertions.assertTrue(allocated.await(30, TimeUnit.SECONDS));

            Future<Long> other = executor.submit(sequences::nextGlobalId);
            long otherId = other.get(5, TimeUnit.SECONDS);

            release.countDown();
            long heldId = holder.get(30, TimeUnit.SECONDS);
            Assertions.assertNotEquals(heldId, otherId);
        } finally {
            release.countDown();
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    /**
     * An ID handed out inside a transaction that rolls back is never handed out again.
     */
    @Test
    public void testIdsAreNotReusedAfterRollback() {
        SqlSequenceRepository sequences = sequences();
        long[] rolledBack = new long[1];
        Assertions.assertThrows(IllegalStateException.class, () -> handles.withHandle(handle -> {
            rolledBack[0] = sequences.nextGlobalIdRaw(handle);
            throw new IllegalStateException("force rollback");
        }));
        Assertions.assertTrue(sequences.nextGlobalId() > rolledBack[0]);
    }

    /**
     * Concurrent callers on separate connections always receive distinct IDs.
     */
    @Test
    public void testConcurrentIdAllocationIsUnique() throws Exception {
        SqlSequenceRepository sequences = sequences();
        int threads = 8;
        int perThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    Assertions.assertTrue(start.await(30, TimeUnit.SECONDS));
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        ids.add(sequences.nextGlobalId());
                        ids.add(-sequences.nextContentId());
                        ids.add(Long.MIN_VALUE + sequences.nextCommentId());
                    }
                    return ids;
                }));
            }
            start.countDown();
            Set<Long> all = new HashSet<>();
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get(60, TimeUnit.SECONDS));
            }
            Assertions.assertEquals(threads * perThread * 3, all.size());
        } finally {
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    /**
     * After an ID-preserving import, reset moves the sequences past the imported IDs; a reset with
     * nothing above the sequence never moves it backwards.
     */
    @Test
    public void testResetMovesSequencesPastImportedIdsAndNeverBackwards() {
        SqlSequenceRepository sequences = sequences();
        String groupId = TestUtils.generateGroupId();
        ArtifactVersionMetaDataDto first = createFirstVersion(groupId, TestUtils.generateArtifactId());

        long importedGlobalId = sequences.nextGlobalId() + 1000;
        ArtifactVersionEntity imported = duplicateOf(first, "2");
        imported.globalId = importedGlobalId;
        imported.versionOrder = first.getVersionOrder() + 1;
        storage.importArtifactVersion(imported);

        storage.resetGlobalId();
        long afterReset = sequences.nextGlobalId();
        Assertions.assertTrue(afterReset > importedGlobalId,
                "expected > " + importedGlobalId + " but was " + afterReset);

        storage.resetGlobalId();
        Assertions.assertTrue(sequences.nextGlobalId() > afterReset);
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

    /**
     * The 115 upgrader seeds the native sequences above both the legacy sequences table and the
     * IDs present in the data tables.
     */
    @Test
    public void testNativeSequencesUpgraderSeedsAboveLegacyValues() throws Exception {
        Assumptions.assumeFalse("h2".equals(sqlStatements.dbType()), "H2 keeps in-memory counters");
        SqlSequenceRepository sequences = sequences();
        long legacyGlobalId = sequences.nextGlobalId() + 5000;
        long legacyContentId = sequences.nextContentId() + 5000;
        long legacyCommentId = sequences.nextCommentId() + 5000;

        execute("CREATE TABLE sequences (seqName VARCHAR(32) NOT NULL PRIMARY KEY, seqValue BIGINT NOT NULL)");
        try {
            execute("INSERT INTO sequences (seqName, seqValue) VALUES ('globalId', " + legacyGlobalId + ")");
            execute("INSERT INTO sequences (seqName, seqValue) VALUES ('contentId', " + legacyContentId + ")");
            execute("INSERT INTO sequences (seqName, seqValue) VALUES ('commentId', " + legacyCommentId + ")");

            String className = sqlStatements.databaseUpgrade(114, 115).stream()
                    .filter(s -> s.startsWith("UPGRADER:"))
                    .map(s -> s.substring("UPGRADER:".length()).trim())
                    .findFirst().orElseThrow();
            handles.withHandleNoException(handle -> {
                ((IDbUpgrader) Class.forName(className).getConstructor().newInstance()).upgrade(handle);
                return null;
            });

            Assertions.assertTrue(sequences.nextGlobalId() > legacyGlobalId);
            Assertions.assertTrue(sequences.nextContentId() > legacyContentId);
            Assertions.assertTrue(sequences.nextCommentId() > legacyCommentId);
        } finally {
            execute("DROP TABLE sequences");
        }
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
