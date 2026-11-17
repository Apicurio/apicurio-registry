package io.apicurio.registry.storage.impl.sql.upgrader;

import io.apicurio.registry.storage.impl.sql.IDbUpgrader;
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
        handle.createUpdate(hasGeneratedKey(handle) ? REPLACE_GENERATED_KEY : ADD_KEY).execute();
    }

    private static boolean hasGeneratedKey(Handle handle) {
        try {
            handle.createQuery("SELECT my_row_id FROM schema_usage WHERE 1 = 0").map(rs -> 0).list();
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
