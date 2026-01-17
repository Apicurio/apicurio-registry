package io.apicurio.registry.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.factories.CatalogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for ApicurioCatalog.
 *
 * <p>
 * Requires running Apicurio Registry. Set REGISTRY_URL env var.
 */
@EnabledIfEnvironmentVariable(named = "REGISTRY_URL", matches = ".+")
class ApicurioCatalogIT {

    private static final String REG_URL = System.getenv("REGISTRY_URL");

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        final Map<String, String> props = new HashMap<>();
        props.put("type", "apicurio");
        props.put("registry.url", REG_URL);
        props.put("default-database", "default");

        final ApicurioCatalogFactory factory = new ApicurioCatalogFactory();
        final CatalogFactory.Context ctx = new TestContext("test", props);
        catalog = factory.createCatalog(ctx);
        catalog.open();
    }

    @AfterEach
    void tearDown() {
        if (catalog != null) {
            catalog.close();
        }
    }

    @Test
    void testListDatabases() throws Exception {
        final List<String> dbs = catalog.listDatabases();
        assertNotNull(dbs);
    }

    @Test
    void testDefaultDatabaseExists() throws Exception {
        assertTrue(catalog.databaseExists("default"));
    }

    @Test
    void testNonExistentDatabaseDoesNotExist() {
        assertFalse(catalog.databaseExists("non-existent-db-12345"));
    }

    @Test
    void testListTables() throws Exception {
        if (catalog.databaseExists("default")) {
            final List<String> tables = catalog.listTables("default");
            assertNotNull(tables);
        }
    }

    @Test
    void testTableNotExists() {
        final ObjectPath path = new ObjectPath("default", "nonexistent");
        assertFalse(catalog.tableExists(path));
    }

    /**
     * Test context for catalog factory.
     */
    private static final class TestContext implements CatalogFactory.Context {

        private final String catalogName;
        private final Map<String, String> opts;

        TestContext(final String name, final Map<String, String> options) {
            this.catalogName = name;
            this.opts = options;
        }

        @Override
        public String getName() {
            return catalogName;
        }

        @Override
        public Map<String, String> getOptions() {
            return opts;
        }

        @Override
        public Configuration getConfiguration() {
            return new Configuration();
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
