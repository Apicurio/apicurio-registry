package io.apicurio.registry.storage.impl.sql;

import java.sql.SQLException;
import java.util.Set;

/**
 * MySQL implementation of the sql statements interface. Provides sql statements that are specific to MySQL,
 * where applicable.
 */
public class MySQLSqlStatements extends CommonSqlStatements {

    private static final Set<Integer> TRANSIENT_CONFLICT_ERROR_CODES = Set.of(1213);
    private static final int LOCK_WAIT_TIMEOUT_ERROR_CODE = 1205;
    /**
     * Incorrect string value (1366) and data too long (1406). Connector/J 9.6.0 maps 1406 to 22001 but leaves
     * 1366 at the server's HY000 (MysqlErrorNumbers.mysqlToSqlstate), so 1366 is caught only by its code.
     */
    private static final Set<Integer> DATA_ERROR_CODES = Set.of(1366, 1406);

    /**
     * Constructor.
     */
    public MySQLSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "mysql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("Duplicate entry");
    }

    /** Deadlock (1213). */
    @Override
    protected Set<Integer> transientConflictErrorCodes() {
        return TRANSIENT_CONFLICT_ERROR_CODES;
    }

    @Override
    protected Set<Integer> dataErrorCodes() {
        return DATA_ERROR_CODES;
    }

    /**
     * A lock wait timeout (1205) is not a transient conflict: it has already waited for the whole timeout,
     * and a retry would wait again. Connector/J reports it with SQLState 40001, because the server sends
     * HY000 and the driver maps 1205 to 40001 (MysqlErrorNumbers.mysqlToSqlstate), so it is excluded here.
     */
    @Override
    protected boolean isTransientConflictException(SQLException e) {
        return e.getErrorCode() != LOCK_WAIT_TIMEOUT_ERROR_CODE && super.isTransientConflictException(e);
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("foreign key constraint fails");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, 1) ON DUPLICATE KEY UPDATE seqValue = seqValue + 1";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, ?) ON DUPLICATE KEY UPDATE seqValue = ?";
    }

    @Override
    public String upsertBranch() {
        return """
                INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE groupId=VALUES(groupId)
                """;
    }

    @Override
    public String upsertConfigProperty() {
        return "INSERT INTO config (propName, propValue, modifiedOn) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE propValue = VALUES(propValue), modifiedOn = VALUES(modifiedOn)";
    }

    @Override
    public String upsertSchemaUsageDaily() {
        return "INSERT INTO schema_usage_daily (globalId, contentId, clientId, operation, usageDay, fetchCount, firstEventTimestamp, lastEventTimestamp) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE fetchCount = fetchCount + VALUES(fetchCount), "
                + "firstEventTimestamp = LEAST(firstEventTimestamp, VALUES(firstEventTimestamp)), "
                + "lastEventTimestamp = GREATEST(lastEventTimestamp, VALUES(lastEventTimestamp))";
    }

    @Override
    public String selectCountTableTemplate(String countBy, String tableName, String alias,
            String whereClause) {
        return super.selectCountTableTemplate(countBy, "`" + tableName + "`", alias, whereClause);
    }

    @Override
    public String selectTableTemplate(String columns, String tableName, String alias, String whereClause,
            String orderBy) {
        return super.selectTableTemplate(columns, "`" + tableName + "`", alias, whereClause, orderBy);
    }

    @Override
    public String groupsTable() {
        return "`groups`";
    }

    @Override
    public String createDataSnapshot() {
        throw new IllegalStateException("Snapshot creation is not supported for MySQL storage");
    }

    @Override
    public String restoreFromSnapshot() {
        throw new IllegalStateException("Restoring from snapshot is not supported for MySQL storage");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#acquireInitLock()
     */
    @Override
    public String acquireInitLock() {
        // Use MySQL GET_LOCK with 30 second timeout
        // Returns 1 if lock acquired, 0 if timeout, NULL on error
        return "SELECT GET_LOCK('apicurio_init_lock', 30)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        return "SELECT RELEASE_LOCK('apicurio_init_lock')";
    }
}