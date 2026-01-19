package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A specialized row mapper for database lock acquisition/release operations.
 * This mapper handles various return types from different database lock mechanisms:
 * - Integer values (e.g., MySQL's GET_LOCK returns 1/0/NULL)
 * - Boolean values (e.g., PostgreSQL's pg_advisory_unlock returns boolean)
 * - Void/empty results (e.g., PostgreSQL's pg_advisory_lock returns void)
 *
 * The mapper always returns 1 for successful operations, which allows the calling
 * code to verify that the lock operation completed without exceptions.
 */
public class DatabaseLockMapper implements RowMapper<Integer> {

    public static final DatabaseLockMapper instance = new DatabaseLockMapper();

    /**
     * Constructor.
     */
    private DatabaseLockMapper() {
    }

    /**
     * Maps the result set from a database lock operation to an Integer value.
     *
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public Integer map(ResultSet rs) throws SQLException {
        // Check if we have any columns in the result set
        int columnCount = rs.getMetaData().getColumnCount();

        if (columnCount == 0) {
            // No columns returned (e.g., void function) - operation succeeded
            return 1;
        }

        // Try to get the first column value
        Object value = rs.getObject(1);

        if (value == null) {
            // NULL returned - treat as failure
            return 0;
        }

        // Handle different return types
        if (value instanceof Number) {
            // Integer, Long, etc. from functions like MySQL's GET_LOCK
            return ((Number) value).intValue();
        } else if (value instanceof Boolean) {
            // Boolean from functions like PostgreSQL's pg_advisory_unlock
            return ((Boolean) value) ? 1 : 0;
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.isEmpty()) {
                // Empty string (void result) - operation succeeded
                return 1;
            }
            // Try to parse as integer
            try {
                return Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                // Not a number, treat as success if we got here without exception
                return 1;
            }
        }

        // Default: if we got a non-null value, treat as success
        return 1;
    }

}
