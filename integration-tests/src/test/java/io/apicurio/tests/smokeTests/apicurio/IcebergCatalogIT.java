package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.UpdateSchema;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.rest.RESTCatalog;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(Constants.SMOKE)
@Tag(Constants.ICEBERG)
@QuarkusIntegrationTest
class IcebergCatalogIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(IcebergCatalogIT.class);

    private RESTCatalog catalog;

    // Track namespaces and tables created during tests for cleanup
    private final java.util.Deque<TableIdentifier> createdTables = new java.util.ArrayDeque<>();
    private final java.util.Deque<Namespace> createdNamespaces = new java.util.ArrayDeque<>();

    @BeforeAll
    void setupCatalog() {
        catalog = new RESTCatalog();
        Map<String, String> properties = new HashMap<>();
        properties.put(CatalogProperties.URI, getRegistryBaseUrl() + "/apis/iceberg/v1");
        properties.put(CatalogProperties.WAREHOUSE_LOCATION, "/warehouse");
        catalog.initialize("apicurio", properties);
    }

    @AfterEach
    void cleanupTestData() {
        // Drop tables first (LIFO order)
        while (!createdTables.isEmpty()) {
            TableIdentifier table = createdTables.pop();
            try {
                catalog.dropTable(table, true);
            } catch (Exception e) {
                LOGGER.warn("Failed to clean up table {}: {}", table, e.getMessage());
            }
        }
        // Drop namespaces (LIFO order)
        while (!createdNamespaces.isEmpty()) {
            Namespace ns = createdNamespaces.pop();
            try {
                catalog.dropNamespace(ns);
            } catch (Exception e) {
                LOGGER.warn("Failed to clean up namespace {}: {}", ns, e.getMessage());
            }
        }
    }

    @AfterAll
    void closeCatalog() {
        if (catalog != null) {
            catalog.close();
        }
    }

    private String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Namespace createTestNamespace(String prefix) {
        return createTestNamespace(prefix, Map.of());
    }

    private Namespace createTestNamespace(String prefix, Map<String, String> properties) {
        Namespace ns = Namespace.of(uniqueName(prefix));
        catalog.createNamespace(ns, properties);
        createdNamespaces.push(ns);
        return ns;
    }

    private Schema simpleSchema() {
        return new Schema(
                Types.NestedField.required(1, "id", Types.LongType.get()),
                Types.NestedField.optional(2, "name", Types.StringType.get()),
                Types.NestedField.optional(3, "ts", Types.TimestampType.withoutZone())
        );
    }

    private Table createTestTable(Namespace ns, String tableName) {
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);
        Table table = catalog.createTable(tableId, simpleSchema());
        createdTables.push(tableId);
        return table;
    }

    // ========== Catalog Init ==========

    @Test
    void testCatalogInitialization() {
        assertNotNull(catalog, "Catalog should be initialized");
        assertNotNull(catalog.name(), "Catalog name should not be null");
    }

    // ========== Namespace Operations ==========

    @Test
    void testCreateAndListNamespaces() {
        Namespace ns = createTestNamespace("list_ns");

        List<Namespace> namespaces = catalog.listNamespaces();
        assertTrue(namespaces.contains(ns), "Created namespace should be listed");
    }

    @Test
    void testLoadNamespaceMetadata() {
        Map<String, String> props = Map.of("comment", "test namespace", "owner", "test-user");
        Namespace ns = createTestNamespace("load_ns", props);

        Map<String, String> loaded = catalog.loadNamespaceMetadata(ns);
        assertNotNull(loaded);
        assertEquals("test namespace", loaded.get("comment"));
        assertEquals("test-user", loaded.get("owner"));
    }

    @Test
    void testUpdateNamespaceProperties() {
        Namespace ns = createTestNamespace("update_ns", Map.of("key1", "val1"));

        catalog.setProperties(ns, Map.of("key2", "val2"));
        Map<String, String> loaded = catalog.loadNamespaceMetadata(ns);
        assertEquals("val2", loaded.get("key2"));

        catalog.removeProperties(ns, Set.of("key1"));
        loaded = catalog.loadNamespaceMetadata(ns);
        assertFalse(loaded.containsKey("key1"), "Removed property should not be present");
    }

    @Test
    void testDropNamespace() {
        Namespace ns = Namespace.of(uniqueName("drop_ns"));
        catalog.createNamespace(ns);
        // Don't track since we're dropping it manually

        assertTrue(catalog.dropNamespace(ns));
        assertThrows(NoSuchNamespaceException.class, () -> catalog.loadNamespaceMetadata(ns));
    }

    @Test
    void testDropNonEmptyNamespaceFails() {
        Namespace ns = createTestNamespace("nonempty_ns");
        createTestTable(ns, "blocker_table");

        assertThrows(NamespaceNotEmptyException.class, () -> catalog.dropNamespace(ns));
    }

    @Test
    void testNestedNamespaces() {
        Namespace parent = createTestNamespace("parent_ns");
        Namespace child = Namespace.of(parent.level(0), "child");
        catalog.createNamespace(child);
        createdNamespaces.push(child);

        List<Namespace> children = catalog.listNamespaces(parent);
        assertTrue(children.contains(child), "Child namespace should be listed under parent");
    }

    // ========== Table Lifecycle ==========

    @Test
    void testCreateAndLoadTable() {
        Namespace ns = createTestNamespace("tbl_create");
        String tableName = uniqueName("test_table");
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        Table created = catalog.createTable(tableId, simpleSchema());
        createdTables.push(tableId);
        assertNotNull(created);

        Table loaded = catalog.loadTable(tableId);
        assertNotNull(loaded);
        assertEquals(3, loaded.schema().columns().size());
        assertEquals("id", loaded.schema().findField(1).name());
        assertEquals("name", loaded.schema().findField(2).name());
        assertEquals("ts", loaded.schema().findField(3).name());
    }

    @Test
    void testListTables() {
        Namespace ns = createTestNamespace("tbl_list");
        String table1 = uniqueName("table_a");
        String table2 = uniqueName("table_b");
        createTestTable(ns, table1);
        createTestTable(ns, table2);

        List<TableIdentifier> tables = catalog.listTables(ns);
        Set<String> tableNames = tables.stream()
                .map(TableIdentifier::name)
                .collect(Collectors.toSet());
        assertTrue(tableNames.contains(table1));
        assertTrue(tableNames.contains(table2));
    }

    @Test
    void testTableExists() {
        Namespace ns = createTestNamespace("tbl_exists");
        String tableName = uniqueName("exists_tbl");
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        assertFalse(catalog.tableExists(tableId));
        createTestTable(ns, tableName);
        assertTrue(catalog.tableExists(tableId));
    }

    @Test
    void testDropTable() {
        Namespace ns = createTestNamespace("tbl_drop");
        String tableName = uniqueName("drop_tbl");
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);
        catalog.createTable(tableId, simpleSchema());
        // Don't track since we drop manually

        assertTrue(catalog.dropTable(tableId));
        assertFalse(catalog.tableExists(tableId));
    }

    @Test
    void testRenameTable() {
        Namespace ns = createTestNamespace("tbl_rename");
        String originalName = uniqueName("orig_tbl");
        String newName = uniqueName("renamed_tbl");
        TableIdentifier originalId = TableIdentifier.of(ns, originalName);
        TableIdentifier newId = TableIdentifier.of(ns, newName);

        catalog.createTable(originalId, simpleSchema());
        // Track the new name for cleanup
        createdTables.push(newId);

        catalog.renameTable(originalId, newId);
        assertFalse(catalog.tableExists(originalId));
        assertTrue(catalog.tableExists(newId));
    }

    @Test
    void testRenameTableAcrossNamespaces() {
        Namespace ns1 = createTestNamespace("rename_src");
        Namespace ns2 = createTestNamespace("rename_dst");
        String tableName = uniqueName("cross_tbl");
        TableIdentifier srcId = TableIdentifier.of(ns1, tableName);
        TableIdentifier dstId = TableIdentifier.of(ns2, tableName);

        catalog.createTable(srcId, simpleSchema());
        createdTables.push(dstId);

        catalog.renameTable(srcId, dstId);
        assertFalse(catalog.tableExists(srcId));
        assertTrue(catalog.tableExists(dstId));
    }

    // ========== Schema Evolution ==========

    @Test
    void testAddColumns() {
        Namespace ns = createTestNamespace("schema_add");
        Table table = createTestTable(ns, uniqueName("add_col"));

        table.updateSchema()
                .addColumn("email", Types.StringType.get())
                .commit();

        Table reloaded = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        assertNotNull(reloaded.schema().findField("email"), "Added column should exist");
    }

    @Test
    void testRenameColumn() {
        Namespace ns = createTestNamespace("schema_rename");
        Table table = createTestTable(ns, uniqueName("rename_col"));

        table.updateSchema()
                .renameColumn("name", "full_name")
                .commit();

        Table reloaded = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        assertNotNull(reloaded.schema().findField("full_name"), "Renamed column should exist");
    }

    @Test
    void testMakeColumnOptional() {
        Namespace ns = createTestNamespace("schema_opt");
        Table table = createTestTable(ns, uniqueName("opt_col"));

        // "id" is required - make it optional
        table.updateSchema()
                .makeColumnOptional("id")
                .commit();

        Table reloaded = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        assertTrue(reloaded.schema().findField("id").isOptional(), "Column should be optional");
    }

    @Test
    void testSchemaChangePersistence() {
        Namespace ns = createTestNamespace("schema_persist");
        String tableName = uniqueName("persist_tbl");
        Table table = createTestTable(ns, tableName);
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        // Multiple schema changes
        table.updateSchema()
                .addColumn("col_a", Types.IntegerType.get())
                .addColumn("col_b", Types.DoubleType.get())
                .commit();

        table = catalog.loadTable(tableId);
        table.updateSchema()
                .renameColumn("col_a", "column_alpha")
                .commit();

        Table finalTable = catalog.loadTable(tableId);
        assertNotNull(finalTable.schema().findField("column_alpha"));
        assertNotNull(finalTable.schema().findField("col_b"));
    }

    // ========== Table Properties ==========

    @Test
    void testSetTableProperties() {
        Namespace ns = createTestNamespace("props_set");
        Table table = createTestTable(ns, uniqueName("props_tbl"));

        table.updateProperties()
                .set("custom.key1", "value1")
                .set("custom.key2", "value2")
                .commit();

        Table reloaded = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        assertEquals("value1", reloaded.properties().get("custom.key1"));
        assertEquals("value2", reloaded.properties().get("custom.key2"));
    }

    @Test
    void testRemoveTableProperties() {
        Namespace ns = createTestNamespace("props_rm");
        Table table = createTestTable(ns, uniqueName("props_rm_tbl"));

        table.updateProperties()
                .set("temp.key", "temp_value")
                .commit();

        table = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        table.updateProperties()
                .remove("temp.key")
                .commit();

        Table reloaded = catalog.loadTable(TableIdentifier.of(ns, table.name()));
        assertFalse(reloaded.properties().containsKey("temp.key"), "Removed property should not be present");
    }

    @Test
    void testPropertyPersistence() {
        Namespace ns = createTestNamespace("props_persist");
        String tableName = uniqueName("props_p_tbl");
        Table table = createTestTable(ns, tableName);
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        table.updateProperties()
                .set("persist.key", "persist_value")
                .commit();

        // Reload table via catalog to verify persistence
        Table reloaded = catalog.loadTable(tableId);
        assertEquals("persist_value", reloaded.properties().get("persist.key"));
    }

    // ========== Partition Spec ==========

    @Test
    void testCreatePartitionedTable() {
        Namespace ns = createTestNamespace("part_create");
        String tableName = uniqueName("part_tbl");
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        Schema schema = simpleSchema();
        PartitionSpec spec = PartitionSpec.builderFor(schema)
                .identity("name")
                .build();

        Table table = catalog.createTable(tableId, schema, spec);
        createdTables.push(tableId);

        assertFalse(table.spec().isUnpartitioned());
        assertEquals(1, table.spec().fields().size());
    }

    @Test
    void testEvolvePartitionSpec() {
        Namespace ns = createTestNamespace("part_evolve");
        String tableName = uniqueName("evolve_tbl");
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        Table table = catalog.createTable(tableId, simpleSchema());
        createdTables.push(tableId);

        table.updateSpec()
                .addField("name")
                .commit();

        Table reloaded = catalog.loadTable(tableId);
        assertFalse(reloaded.spec().isUnpartitioned());
    }

    // ========== Snapshot Management ==========

    @Disabled("Snapshot operations require data file references and a real file system. " +
            "Apicurio Registry is a metadata-only catalog. See #7259 for Phase 2.")
    @Test
    void testCommitSnapshot() {
        // Requires actual data files to commit
    }

    @Disabled("Snapshot operations require data file references and a real file system. " +
            "Apicurio Registry is a metadata-only catalog. See #7259 for Phase 2.")
    @Test
    void testSnapshotHistory() {
        // Requires actual snapshots
    }

    @Disabled("Snapshot operations require data file references and a real file system. " +
            "Apicurio Registry is a metadata-only catalog. See #7259 for Phase 2.")
    @Test
    void testSnapshotRefs() {
        // Requires actual snapshots
    }

    // ========== Optimistic Concurrency ==========

    @Test
    void testConcurrentSchemaEvolution() throws Exception {
        Namespace ns = createTestNamespace("conc_schema");
        String tableName = uniqueName("conc_tbl");
        Table table = createTestTable(ns, tableName);
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        // Load two references to the same table
        Table ref1 = catalog.loadTable(tableId);
        Table ref2 = catalog.loadTable(tableId);

        // First update succeeds
        ref1.updateSchema()
                .addColumn("col_first", Types.StringType.get())
                .commit();

        // Second update on stale reference should fail with CommitFailedException
        UpdateSchema staleUpdate = ref2.updateSchema()
                .addColumn("col_second", Types.StringType.get());

        assertThrows(org.apache.iceberg.exceptions.CommitFailedException.class, staleUpdate::commit);
    }

    @Test
    void testConcurrentPropertyUpdates() throws Exception {
        Namespace ns = createTestNamespace("conc_props");
        String tableName = uniqueName("conc_prop_tbl");
        Table table = createTestTable(ns, tableName);
        TableIdentifier tableId = TableIdentifier.of(ns, tableName);

        Table ref1 = catalog.loadTable(tableId);
        Table ref2 = catalog.loadTable(tableId);

        ref1.updateProperties()
                .set("key", "value1")
                .commit();

        assertThrows(org.apache.iceberg.exceptions.CommitFailedException.class,
                () -> ref2.updateProperties()
                        .set("key", "value2")
                        .commit());
    }

    // ========== Error Cases ==========

    @Test
    void testLoadNonExistentTable() {
        Namespace ns = createTestNamespace("err_ns");
        TableIdentifier nonExistent = TableIdentifier.of(ns, "does_not_exist");

        assertThrows(NoSuchTableException.class, () -> catalog.loadTable(nonExistent));
    }

    @Test
    void testLoadNonExistentNamespace() {
        Namespace nonExistent = Namespace.of("ns_does_not_exist_" + UUID.randomUUID());
        assertThrows(NoSuchNamespaceException.class, () -> catalog.loadNamespaceMetadata(nonExistent));
    }
}
