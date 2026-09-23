package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.PeerNotFoundException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.repositories.SqlPeerRepository;
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
 * Executes the real 109-to-110 upgrade path end to end, instead of only checking that the
 * upgrade DDL text mentions the peers table. Bootstraps a throwaway H2 database from the exact
 * pre-peers schema this project shipped before this change, seeds it with a representative
 * pre-existing row, runs {@link SqlStatements#databaseUpgrade(int, int)} for real (the same
 * method the production upgrade path calls), and then exercises the peer repository against the
 * upgraded schema.
 */
class PeersDdlUpgradeTest {

    private static final Logger LOG = LoggerFactory.getLogger(PeersDdlUpgradeTest.class);

    @Test
    void testRealUpgradeFrom109PreservesDataAndAddsPeers() throws Exception {
        SqlStatements sqlStatements = new H2SqlStatements();
        AgroalDataSource dataSource = createThrowawayH2DataSource();
        try {
            HandleFactory handles = new DefaultHandleFactory(dataSource, LOG, new ConnectionRetryConfig());

            // 1. Bootstrap the real pre-peers (db_version 109) schema, as it existed before this change.
            handles.withHandleNoException((Handle handle) -> {
                for (String statement : parseDdl("pre-peers-schema/h2.ddl")) {
                    handle.createUpdate(statement).execute();
                }
                return null;
            });

            // 2. Seed representative pre-existing data that the migration must not touch.
            String principalId = "pre-existing-principal-" + UUID.randomUUID();
            handles.withHandleNoException((Handle handle) -> {
                handle.createUpdate("INSERT INTO acls (principalId, role, principalName) VALUES (?, ?, ?)")
                        .bind(0, principalId).bind(1, "sr-admin").bind(2, "Pre-existing Admin").execute();
                return null;
            });

            // Sanity check: confirm the pre-upgrade version and that peers does not exist yet.
            Assertions.assertEquals(109, currentDbVersion(handles));
            Assertions.assertFalse(tableExists(handles, "peers"));

            // 3. Run the real upgrade path: the exact statement list the production
            // AbstractSqlRegistryStorage#upgradeDatabaseRaw executes for a 109 -> 110 database,
            // parsed from the packaged upgrades/110/h2.upgrade.ddl resource.
            List<String> upgradeStatements = sqlStatements.databaseUpgrade(109, 110);
            Assertions.assertFalse(upgradeStatements.isEmpty(), "Expected at least one upgrade statement.");
            handles.withHandleNoException((Handle handle) -> {
                for (String statement : upgradeStatements) {
                    handle.createUpdate(statement).execute();
                }
                return null;
            });

            // 4. Assert the version was actually bumped and the peers table actually exists now.
            Assertions.assertEquals(110, currentDbVersion(handles));
            Assertions.assertTrue(tableExists(handles, "peers"));

            // 5. Assert pre-existing data survived the upgrade untouched.
            Map<String, String> preExisting = handles.withHandleNoException((Handle handle) -> handle
                    .createQuery("SELECT role, principalName FROM acls WHERE principalId = ?")
                    .bind(0, principalId)
                    .map(rs -> {
                        Map<String, String> row = new HashMap<>();
                        row.put("role", rs.getString("role"));
                        row.put("principalName", rs.getString("principalName"));
                        return row;
                    }).one());
            Assertions.assertEquals("sr-admin", preExisting.get("role"));
            Assertions.assertEquals("Pre-existing Admin", preExisting.get("principalName"));

            // 6. Exercise peer create/read/update/delete through the real repository against the
            // now-upgraded schema.
            SqlPeerRepository peerRepository = new SqlPeerRepository(handles, sqlStatements, LOG);

            String peerId = "upgrade-test-peer";
            PeerDto peer = PeerDto.builder().peerId(peerId).url("https://peer.example.com")
                    .name("Upgrade Test Peer").enabled(true).credentialSecretRef("upgrade-test-cred")
                    .build();
            peerRepository.createPeer(peer);

            PeerDto fetched = peerRepository.getPeer(peerId);
            Assertions.assertEquals(peerId, fetched.getPeerId());
            Assertions.assertEquals("https://peer.example.com", fetched.getUrl());
            Assertions.assertEquals("Upgrade Test Peer", fetched.getName());
            Assertions.assertTrue(fetched.isEnabled());
            Assertions.assertEquals("upgrade-test-cred", fetched.getCredentialSecretRef());

            PeerDto updated = PeerDto.builder().peerId(peerId).url("https://peer-updated.example.com")
                    .name("Upgrade Test Peer").enabled(false).credentialSecretRef("upgrade-test-cred")
                    .build();
            peerRepository.updatePeer(updated);
            Assertions.assertEquals("https://peer-updated.example.com", peerRepository.getPeer(peerId).getUrl());
            Assertions.assertFalse(peerRepository.getPeer(peerId).isEnabled());

            peerRepository.deletePeer(peerId);
            Assertions.assertThrows(PeerNotFoundException.class, () -> peerRepository.getPeer(peerId));
        } finally {
            dataSource.close();
        }
    }

    private int currentDbVersion(HandleFactory handles) {
        return Integer.parseInt(handles.withHandleNoException((Handle handle) -> handle
                .createQuery("SELECT propValue FROM apicurio WHERE propName = ?")
                .bind(0, "db_version").map(rs -> rs.getString("propValue")).one()));
    }

    private boolean tableExists(HandleFactory handles, String tableName) {
        // Case-insensitive: H2's information_schema identifier casing depends on the database
        // mode, and this throwaway datasource is not guaranteed to fold identifiers the same
        // way the application's own configured datasource does.
        return handles.withHandleNoException((Handle handle) -> handle
                .createQuery(
                        "SELECT count(*) AS count FROM information_schema.tables WHERE UPPER(table_name) = UPPER(?)")
                .bind(0, tableName).map(rs -> rs.getInt("count")).one() > 0);
    }

    private List<String> parseDdl(String resourcePath) {
        DdlParser parser = new DdlParser();
        try (InputStream input = PeersDdlUpgradeTest.class.getResourceAsStream(resourcePath)) {
            Assertions.assertNotNull(input, "Missing test fixture: " + resourcePath);
            return parser.parse(input);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private AgroalDataSource createThrowawayH2DataSource() throws Exception {
        Map<String, String> props = new HashMap<>();
        String jdbcUrl = "jdbc:h2:mem:peers-upgrade-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
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
