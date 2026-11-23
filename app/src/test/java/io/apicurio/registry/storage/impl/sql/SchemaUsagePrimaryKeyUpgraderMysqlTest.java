package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.upgrader.SchemaUsagePrimaryKeyUpgrader;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the 115-to-116 schema_usage upgrade against a MySQL server configured the way managed and
 * InnoDB Cluster deployments often are: sql_require_primary_key and
 * sql_generate_invisible_primary_key both ON. Existing tables are created the way released versions
 * created them: either with the invisible key the server generates or, as on a server where the
 * settings were turned on later, with no key at all.
 */
@Tag(ApicurioTestTags.SLOW)
class SchemaUsagePrimaryKeyUpgraderMysqlTest {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaUsagePrimaryKeyUpgraderMysqlTest.class);

    private static final MySQLContainer DATABASE = new MySQLContainer(
            DockerImageName.parse("mysql:8").asCompatibleSubstituteFor("mysql"))
            .withCommand("--sql-require-primary-key=ON", "--sql-generate-invisible-primary-key=ON");

    private static AgroalDataSource dataSource;
    private static HandleFactory handles;

    /** schema_usage as the 105 upgrade script created it up to release 3.3.x, with no primary key. */
    private static final List<String> RELEASED_105_TABLE = List.of(
            "CREATE TABLE schema_usage (globalId BIGINT NOT NULL, contentId BIGINT NOT NULL DEFAULT 0,"
                    + " clientId VARCHAR(256) NOT NULL, operation VARCHAR(32) NOT NULL,"
                    + " eventTimestamp BIGINT NOT NULL, recordedOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
                    + " DEFAULT CHARACTER SET ascii COLLATE ascii_general_ci",
            "CREATE INDEX IDX_schema_usage_1 ON schema_usage(globalId)",
            "CREATE INDEX IDX_schema_usage_2 ON schema_usage(clientId)",
            "CREATE INDEX IDX_schema_usage_3 ON schema_usage(eventTimestamp)");

    private final SqlStatements sqlStatements = new MySQLSqlStatements();

    @BeforeAll
    static void startDatabase() throws Exception {
        DATABASE.start();
        Map<String, String> props = new HashMap<>();
        props.put(AgroalPropertiesReader.MAX_SIZE, "1");
        props.put(AgroalPropertiesReader.JDBC_URL, DATABASE.getJdbcUrl());
        props.put(AgroalPropertiesReader.PRINCIPAL, "root");
        props.put(AgroalPropertiesReader.CREDENTIAL, DATABASE.getPassword());
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, RegistryDatabaseKind.mysql.getDriverClassName());
        dataSource = AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props).get());
        handles = new DefaultHandleFactory(dataSource, LOG, new ConnectionRetryConfig());
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
        DATABASE.stop();
    }

    /** The pool holds one connection, so session settings a failed test left behind are reset too. */
    @BeforeEach
    void dropTable() {
        execute("SET SESSION sql_require_primary_key = DEFAULT");
        execute("SET SESSION sql_generate_invisible_primary_key = DEFAULT");
        execute("DROP TABLE IF EXISTS schema_usage");
    }

    @Test
    void testReplacesGeneratedInvisiblePrimaryKey() {
        createReleasedSchemaUsage(true);
        Assertions.assertEquals(List.of("my_row_id"), primaryKeyColumns());

        upgradeAndAssertRowsKept();
    }

    @Test
    void testAddsPrimaryKeyToKeylessTable() {
        createReleasedSchemaUsage(false);
        Assertions.assertEquals(List.of(), primaryKeyColumns());

        upgradeAndAssertRowsKept();
    }

    @Test
    void testKeepsPrimaryKeyAddedByHand() {
        createReleasedSchemaUsage(false);
        execute("ALTER TABLE schema_usage ADD COLUMN usageId BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST");

        upgradeAndAssertRowsKept();
    }

    /**
     * An upgrade from before 105 creates the table with its key, without the server generating one,
     * and this upgrade then leaves it alone.
     */
    @Test
    void testKeepsPrimaryKeyCreatedBy105() {
        handles.withHandleNoException((Handle handle) -> {
            handle.createUpdate("SET SESSION sql_generate_invisible_primary_key = OFF").execute();
            for (String statement : sqlStatements.databaseUpgrade(104, 105)) {
                if (statement.contains("schema_usage")) {
                    handle.createUpdate(statement).execute();
                }
            }
            handle.createUpdate("SET SESSION sql_generate_invisible_primary_key = DEFAULT").execute();
            return null;
        });
        Assertions.assertEquals(List.of("usageId"), primaryKeyColumns());

        upgradeAndAssertRowsKept();
    }

    /**
     * Before 105 declared the key, it failed on a server that requires one and generates none, after
     * setting db_version to 105, so the table was never created.
     */
    @Test
    void testCreatesMissingTable() {
        upgrade();

        Assertions.assertEquals(List.of("usageId"), primaryKeyColumns());
        Assertions.assertEquals(3, count("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics"
                + " WHERE table_schema = DATABASE() AND table_name = 'schema_usage'"
                + " AND index_name LIKE 'IDX_schema_usage_%'"));
        insertUsage(10L, "client-a");
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM schema_usage"));
    }

    private void upgradeAndAssertRowsKept() {
        insertUsage(10L, "client-a");
        insertUsage(20L, "client-b");

        upgrade();

        Assertions.assertEquals(List.of("usageId"), primaryKeyColumns());
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = 'schema_usage' AND column_name = 'my_row_id'"));

        insertUsage(30L, "client-c");
        List<String> rows = handles.withHandleNoException((Handle handle) -> handle
                .createQuery("SELECT usageId, globalId, clientId FROM schema_usage ORDER BY usageId")
                .map(rs -> rs.getLong("usageId") + ":" + rs.getLong("globalId") + ":" + rs.getString("clientId"))
                .list());
        Assertions.assertEquals(List.of("1:10:client-a", "2:20:client-b", "3:30:client-c"), rows);
    }

    private void upgrade() {
        handles.withHandleNoException((Handle handle) -> {
            new SchemaUsagePrimaryKeyUpgrader().upgrade(handle);
            return null;
        });
    }

    private void createReleasedSchemaUsage(boolean generateInvisibleKey) {
        handles.withHandleNoException((Handle handle) -> {
            if (!generateInvisibleKey) {
                handle.createUpdate("SET SESSION sql_require_primary_key = OFF").execute();
                handle.createUpdate("SET SESSION sql_generate_invisible_primary_key = OFF").execute();
            }
            for (String statement : RELEASED_105_TABLE) {
                handle.createUpdate(statement).execute();
            }
            handle.createUpdate("SET SESSION sql_require_primary_key = DEFAULT").execute();
            handle.createUpdate("SET SESSION sql_generate_invisible_primary_key = DEFAULT").execute();
            return null;
        });
    }

    private void insertUsage(long globalId, String clientId) {
        handles.withHandleNoException((Handle handle) -> {
            handle.createUpdate(sqlStatements.insertSchemaUsage()).bind(0, globalId).bind(1, 0L)
                    .bind(2, clientId).bind(3, "FETCH").bind(4, System.currentTimeMillis()).execute();
            return null;
        });
    }

    private List<String> primaryKeyColumns() {
        return handles.withHandleNoException((Handle handle) -> handle
                .createQuery("SELECT column_name FROM information_schema.key_column_usage"
                        + " WHERE table_schema = DATABASE() AND table_name = 'schema_usage'"
                        + " AND constraint_name = 'PRIMARY' ORDER BY ordinal_position")
                .map(rs -> rs.getString(1)).list());
    }

    private int count(String sql) {
        return handles.withHandleNoException((Handle handle) -> handle.createQuery(sql)
                .map(rs -> rs.getInt(1)).one());
    }

    private void execute(String sql) {
        handles.withHandleNoException((Handle handle) -> {
            handle.createUpdate(sql).execute();
            return null;
        });
    }
}
