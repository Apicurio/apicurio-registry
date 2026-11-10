package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the 111-to-112 upgrade step that deletes stale rows from the "drafts" branch for versions
 * that are not in DRAFT state (e.g. promoted versions left on drafts due to issue #10523).
 * Modeled on {@link PeersDdlUpgradeTest}.
 */
class DraftsBranchDdlUpgradeTest {

    private static final Logger LOG = LoggerFactory.getLogger(DraftsBranchDdlUpgradeTest.class);

    private static final String DEFAULT_GROUP = "__$GROUPID$__";
    private static final String CUSTOM_GROUP = "custom-group";

    @Test
    void testUpgradeFrom111RemovesStaleDraftsEntries() throws Exception {
        SqlStatements sqlStatements = new H2SqlStatements();
        AgroalDataSource dataSource = createThrowawayH2DataSource();
        try {
            HandleFactory handles = new DefaultHandleFactory(dataSource, LOG, new ConnectionRetryConfig());

            // 1. Bootstrap the previous schema (version 111).
            // Start from 110 schema and apply the 110 -> 111 upgrade.
            handles.withHandleNoException((Handle handle) -> {
                for (String statement : parseDdl("pre-peers-schema/h2.ddl")) {
                    handle.createUpdate(statement).execute();
                }
                for (String statement : sqlStatements.databaseUpgrade(110, 111)) {
                    handle.createUpdate(statement).execute();
                }
                return null;
            });

            Assertions.assertEquals(111, currentDbVersion(handles));

            // 2. Seed test data for both default group and custom group:
            // One stale drafts entry for a version in ENABLED state, and one real DRAFT version
            // that is legitimately on drafts. Also seed the "latest" branch entry for the ENABLED version.
            handles.withHandleNoException((Handle handle) -> {
                // Insert dummy content
                handle.createUpdate(
                        "INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content) VALUES (1, 'hash1', 'hash1', 'application/json', X'0102')")
                        .execute();

                seedArtifactWithDraftAndStaleDraft(handle, DEFAULT_GROUP, "art-default", 101L, 102L);
                seedArtifactWithDraftAndStaleDraft(handle, CUSTOM_GROUP, "art-custom", 201L, 202L);
                return null;
            });

            // Confirm pre-upgrade state
            for (String groupId : List.of(DEFAULT_GROUP, CUSTOM_GROUP)) {
                String artifactId = groupId.equals(DEFAULT_GROUP) ? "art-default" : "art-custom";
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "drafts", "1.0"));
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "drafts", "2.0"));
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "latest", "1.0"));
            }

            // 3. Run the 111 -> 112 databaseUpgrade
            List<String> upgradeStatements = sqlStatements.databaseUpgrade(111, 112);
            Assertions.assertFalse(upgradeStatements.isEmpty(), "Expected at least one upgrade statement.");
            handles.withHandleNoException((Handle handle) -> {
                for (String statement : upgradeStatements) {
                    handle.createUpdate(statement).execute();
                }
                return null;
            });

            // 4. Assert db_version bumped to 112
            Assertions.assertEquals(112, currentDbVersion(handles));

            // 5. Assert that only the stale entry is gone and real DRAFT entry is still on drafts.
            // Also assert the stale version itself and its "latest" branch entry were not deleted.
            for (String groupId : List.of(DEFAULT_GROUP, CUSTOM_GROUP)) {
                String artifactId = groupId.equals(DEFAULT_GROUP) ? "art-default" : "art-custom";

                // Stale entry removed from drafts
                Assertions.assertFalse(isVersionOnBranch(handles, groupId, artifactId, "drafts", "1.0"),
                        "Stale version 1.0 should have been removed from drafts for group " + groupId);

                // Real DRAFT entry still on drafts
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "drafts", "2.0"),
                        "Legitimate draft 2.0 should still be on drafts for group " + groupId);

                // Stale version itself still exists in versions table
                Assertions.assertTrue(versionExists(handles, groupId, artifactId, "1.0"),
                        "Version 1.0 should not be deleted from versions table for group " + groupId);

                // Stale version's 'latest' branch entry was not deleted
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "latest", "1.0"),
                        "Version 1.0 on 'latest' branch should not be deleted for group " + groupId);
            }

            // 6. Test idempotency: re-running the upgrade script must succeed and preserve rows
            handles.withHandleNoException((Handle handle) -> {
                for (String statement : upgradeStatements) {
                    handle.createUpdate(statement).execute();
                }
                return null;
            });

            Assertions.assertEquals(112, currentDbVersion(handles));
            for (String groupId : List.of(DEFAULT_GROUP, CUSTOM_GROUP)) {
                String artifactId = groupId.equals(DEFAULT_GROUP) ? "art-default" : "art-custom";
                Assertions.assertFalse(isVersionOnBranch(handles, groupId, artifactId, "drafts", "1.0"));
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "drafts", "2.0"));
                Assertions.assertTrue(versionExists(handles, groupId, artifactId, "1.0"));
                Assertions.assertTrue(isVersionOnBranch(handles, groupId, artifactId, "latest", "1.0"));
            }
        } finally {
            dataSource.close();
        }
    }

    private void seedArtifactWithDraftAndStaleDraft(Handle handle, String groupId, String artifactId,
            long globalId1, long globalId2) {
        // Create artifact
        handle.createUpdate(
                "INSERT INTO artifacts (groupId, artifactId, type, createdOn, modifiedOn) VALUES (?, ?, 'OPENAPI', NOW(), NOW())")
                .bind(0, groupId).bind(1, artifactId).execute();

        // Create branches: drafts and latest
        handle.createUpdate(
                "INSERT INTO branches (groupId, artifactId, branchId, systemDefined, createdOn, modifiedOn) VALUES (?, ?, 'drafts', TRUE, NOW(), NOW())")
                .bind(0, groupId).bind(1, artifactId).execute();
        handle.createUpdate(
                "INSERT INTO branches (groupId, artifactId, branchId, systemDefined, createdOn, modifiedOn) VALUES (?, ?, 'latest', TRUE, NOW(), NOW())")
                .bind(0, groupId).bind(1, artifactId).execute();

        // Version 1.0: ENABLED
        handle.createUpdate(
                "INSERT INTO versions (globalId, groupId, artifactId, version, versionOrder, state, createdOn, modifiedOn, contentId) VALUES (?, ?, ?, '1.0', 1, 'ENABLED', NOW(), NOW(), 1)")
                .bind(0, globalId1).bind(1, groupId).bind(2, artifactId).execute();

        // Version 2.0: DRAFT
        handle.createUpdate(
                "INSERT INTO versions (globalId, groupId, artifactId, version, versionOrder, state, createdOn, modifiedOn, contentId) VALUES (?, ?, ?, '2.0', 2, 'DRAFT', NOW(), NOW(), 1)")
                .bind(0, globalId2).bind(1, groupId).bind(2, artifactId).execute();

        // branch_versions:
        // Version 1.0 on drafts (stale)
        handle.createUpdate(
                "INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) VALUES (?, ?, 'drafts', 1, '1.0')")
                .bind(0, groupId).bind(1, artifactId).execute();
        // Version 1.0 on latest
        handle.createUpdate(
                "INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) VALUES (?, ?, 'latest', 1, '1.0')")
                .bind(0, groupId).bind(1, artifactId).execute();
        // Version 2.0 on drafts (real)
        handle.createUpdate(
                "INSERT INTO branch_versions (groupId, artifactId, branchId, branchOrder, version) VALUES (?, ?, 'drafts', 2, '2.0')")
                .bind(0, groupId).bind(1, artifactId).execute();
    }

    private boolean isVersionOnBranch(HandleFactory handles, String groupId, String artifactId,
            String branchId, String version) {
        return handles.withHandleNoException((Handle handle) -> handle
                .createQuery(
                        "SELECT count(*) AS count FROM branch_versions WHERE groupId = ? AND artifactId = ? AND branchId = ? AND version = ?")
                .bind(0, groupId).bind(1, artifactId).bind(2, branchId).bind(3, version)
                .map(rs -> rs.getInt("count")).one() > 0);
    }

    private boolean versionExists(HandleFactory handles, String groupId, String artifactId, String version) {
        return handles.withHandleNoException((Handle handle) -> handle
                .createQuery(
                        "SELECT count(*) AS count FROM versions WHERE groupId = ? AND artifactId = ? AND version = ?")
                .bind(0, groupId).bind(1, artifactId).bind(2, version)
                .map(rs -> rs.getInt("count")).one() > 0);
    }

    private int currentDbVersion(HandleFactory handles) {
        return Integer.parseInt(handles.withHandleNoException((Handle handle) -> handle
                .createQuery("SELECT propValue FROM apicurio WHERE propName = ?")
                .bind(0, "db_version").map(rs -> rs.getString("propValue")).one()));
    }

    private List<String> parseDdl(String resourcePath) {
        DdlParser parser = new DdlParser();
        try (InputStream input = DraftsBranchDdlUpgradeTest.class.getResourceAsStream(resourcePath)) {
            Assertions.assertNotNull(input, "Missing test fixture: " + resourcePath);
            return parser.parse(input);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private AgroalDataSource createThrowawayH2DataSource() throws Exception {
        Map<String, String> props = new HashMap<>();
        String jdbcUrl = "jdbc:h2:mem:drafts-upgrade-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        props.put(AgroalPropertiesReader.MAX_SIZE, "5");
        props.put(AgroalPropertiesReader.MIN_SIZE, "1");
        props.put(AgroalPropertiesReader.INITIAL_SIZE, "1");
        props.put(AgroalPropertiesReader.JDBC_URL, jdbcUrl);
        props.put(AgroalPropertiesReader.PRINCIPAL, "sa");
        props.put(AgroalPropertiesReader.CREDENTIAL, "sa");
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, RegistryDatabaseKind.h2.getDriverClassName());
        return AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props).get());
    }
}
