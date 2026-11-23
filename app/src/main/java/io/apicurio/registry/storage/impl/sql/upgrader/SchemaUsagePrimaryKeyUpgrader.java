package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
import io.apicurio.registry.storage.impl.sql.MySQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.sql.SQLException;

/**
 * MySQL only. Adds the usageId primary key to schema_usage, which version 105 created without one.
 * <p>
 * A server running with sql_generate_invisible_primary_key=ON gave the table an invisible my_row_id
 * primary key when it was created. Adding a second key fails, so in that case the generated key is
 * replaced in the same statement (MySQL rejects dropping it on its own while the setting is ON). The
 * generated column is detected by selecting it: it is hidden from information_schema when
 * show_gipk_in_create_table_and_information_schema is OFF, but can always be selected by name.
 * <p>
 * A table that already has usageId is left alone. Version 105 now creates it that way, and operators
 * who could not wait for this upgrade (for example to migrate to InnoDB Cluster) add the same key by
 * hand, so adding it again would fail.
 * <p>
 * A missing table is created from the 105 script. Before 105 declared the key, that script failed
 * on a server with sql_require_primary_key=ON and no invisible key generation, but only after it had
 * set db_version to 105: MySQL commits DDL implicitly. The next start then upgraded on from 105, and
 * the database has had no schema_usage table since.
 */
@RegisterForReflection
public class SchemaUsagePrimaryKeyUpgrader implements IDbUpgrader {

    private static final int ER_BAD_FIELD_ERROR = 1054;

    private static final String ADD_KEY = "ALTER TABLE schema_usage"
            + " ADD COLUMN usageId BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST";

    private static final String REPLACE_GENERATED_KEY = "ALTER TABLE schema_usage"
            + " DROP PRIMARY KEY, DROP COLUMN my_row_id,"
            + " ADD COLUMN usageId BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST";

    @Override
    public void upgrade(Handle handle) {
        if (!hasTable(handle)) {
            createTable(handle);
            return;
        }
        if (hasColumn(handle, "usageId")) {
            return;
        }
        handle.createUpdate(hasColumn(handle, "my_row_id") ? REPLACE_GENERATED_KEY : ADD_KEY).execute();
    }

    private static boolean hasTable(Handle handle) {
        return handle.createQuery("SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_name = 'schema_usage'")
                .map(rs -> rs.getInt(1)).one() > 0;
    }

    private static void createTable(Handle handle) {
        for (String statement : new MySQLSqlStatements().databaseUpgrade(104, 105)) {
            if (statement.contains("schema_usage")) {
                handle.createUpdate(statement).execute();
            }
        }
    }

    private static boolean hasColumn(Handle handle, String column) {
        try {
            handle.createQuery("SELECT " + column + " FROM schema_usage WHERE 1 = 0").map(rs -> 0).list();
            return true;
        } catch (RuntimeSqlException e) {
            if (e.getCause() instanceof SQLException sqlException
                    && sqlException.getErrorCode() == ER_BAD_FIELD_ERROR) {
                return false;
            }
            throw e;
        }
    }
}
