package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.apicurio.registry.storage.impl.sql.upgrader.StructuredContentUpgrader;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Set;
import io.apicurio.registry.storage.dto.SearchFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(MysqlTestProfile.class)
class StructuredContentSearchMysqlTest extends StructuredContentSearchTest {
    @Test
    void failedBackfillKeepsOldMarkerAndUpgradeCanBeRetried() throws Exception {
        String group=group();
        String skill="migration" + UUID.randomUUID().toString().replace("-", "");
        create(group,"agent",skill);
        String script;
        try (var stream=getClass().getResourceAsStream(
                "/io/apicurio/registry/storage/impl/sql/upgrades/110/mysql.upgrade.ddl")) {
            assertNotNull(stream);
            script=new String(stream.readAllBytes(),StandardCharsets.UTF_8)
                    .replaceAll("(?m)^--.*$", "");
        }
        String constraint="migration_" + UUID.randomUUID().toString().replace("-", "");
        handles.withHandleNoException(handle -> {
            handle.createUpdate("DELETE FROM artifact_structured_content WHERE groupId = ?").bind(0,group).execute();
            handle.createUpdate("ALTER TABLE artifact_structured_content ADD CONSTRAINT " + constraint
                    + " CHECK (elementValue <> '" + skill + "')").execute();
            handle.createUpdate("UPDATE apicurio SET propValue = '109' WHERE propName = 'db_version'").execute();
            return null;
        });
        try {
            String upgrade=script;
            assertThrows(RuntimeSqlException.class, () -> runUpgrade(upgrade));
            assertEquals("109", databaseVersion());
        } finally {
            handles.withHandleNoException(handle -> {
                handle.createUpdate("ALTER TABLE artifact_structured_content DROP CONSTRAINT " + constraint).execute();
                return null;
            });
        }
        runUpgrade(script);
        assertEquals("110", databaseVersion());
        assertEquals(Set.of("agent"),matches(group,SearchFilter.ofStructure("skill:" + skill)));
    }

    private void runUpgrade(String script) {
        handles.withHandleNoException(handle -> {
            for (String command : script.split(";")) {
                if (command.trim().startsWith("UPGRADER:")) {
                    new StructuredContentUpgrader().upgrade(handle);
                } else if (!command.isBlank()) {
                    handle.createUpdate(command.trim()).execute();
                }
            }
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
