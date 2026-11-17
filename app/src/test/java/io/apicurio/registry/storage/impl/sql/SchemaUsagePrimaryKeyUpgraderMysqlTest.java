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
 * Runs the 113-to-114 schema_usage upgrade against a MySQL server configured the way managed and
 * InnoDB Cluster deployments often are: sql_require_primary_key and
 * sql_generate_invisible_primary_key both ON. schema_usage is created from the real 105 upgrade
 * script, either with the invisible key the server generates or, as on a server where the settings
 * were turned on later, with no key at all.
 */
@Tag(ApicurioTestTags.SLOW)
class SchemaUsagePrimaryKeyUpgraderMysqlTest {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaUsagePrimaryKeyUpgraderMysqlTest.class);

    private static final MySQLContainer DATABASE = new MySQLContainer(
            DockerImageName.parse("mysql:8").asCompatibleSubstituteFor("mysql"))
            .withCommand("--sql-require-primary-key=ON", "--sql-generate-invisible-primary-key=ON");

    private static AgroalDataSource dataSource;
    private static HandleFactory handles;

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

    @BeforeEach
    void dropTable() {
        execute("DROP TABLE IF EXISTS schema_usage");
    }

    @Test
    void testReplacesGeneratedInvisiblePrimaryKey() {
        createSchemaUsageFrom105(true);
        Assertions.assertEquals(List.of("my_row_id"), primaryKeyColumns());

        upgradeAndAssertRowsKept();
    }

    @Test
    void testAddsPrimaryKeyToKeylessTable() {
        createSchemaUsageFrom105(false);
        Assertions.assertEquals(List.of(), primaryKeyColumns());

        upgradeAndAssertRowsKept();
    }

    @Test
    void testKeepsPrimaryKeyAddedByHand() {
        createSchemaUsageFrom105(false);
        execute("ALTER TABLE schema_usage ADD COLUMN usageId BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST");

        upgradeAndAssertRowsKept();
    }

    private void upgradeAndAssertRowsKept() {
        insertUsage(10L, "client-a");
        insertUsage(20L, "client-b");

        handles.withHandleNoException((Handle handle) -> {
            new SchemaUsagePrimaryKeyUpgrader().upgrade(handle);
            return null;
        });

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

    private void createSchemaUsageFrom105(boolean generateInvisibleKey) {
        List<String> statements = sqlStatements.databaseUpgrade(104, 105).stream()
                .filter(statement -> statement.contains("schema_usage")).toList();
        handles.withHandleNoException((Handle handle) -> {
            if (!generateInvisibleKey) {
                handle.createUpdate("SET SESSION sql_require_primary_key = OFF").execute();
                handle.createUpdate("SET SESSION sql_generate_invisible_primary_key = OFF").execute();
            }
            for (String statement : statements) {
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
