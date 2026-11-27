package io.apicurio.registry.storage.impl.sql;

/**
 * MySQL implementation of the sql statements interface. Provides sql statements that are specific to MySQL,
 * where applicable.
 */
public class MySQLSqlStatements extends CommonSqlStatements {

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

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("foreign key constraint fails");
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
        // GET_LOCK returns 1 if lock acquired, 0 if timeout, NULL on error; map the failures to
        // negative values so the caller can detect them the same way for every database
        return "SELECT CASE GET_LOCK('apicurio_init_lock', 30) WHEN 1 THEN 1 WHEN 0 THEN -1 ELSE -999 END";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        return "SELECT RELEASE_LOCK('apicurio_init_lock')";
    }

    /**
     * Inserts a ticket row into the AUTO_INCREMENT ticket table.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#nextSequenceValue(String)
     */
    @Override
    public String nextSequenceValue(String sequenceName) {
        return "INSERT INTO " + nativeSequenceName(sequenceName) + " () VALUES ()";
    }

    /**
     * Reads the AUTO_INCREMENT value generated on this connection.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectLastInsertedSequenceValue()
     */
    @Override
    public String selectLastInsertedSequenceValue() {
        return "SELECT LAST_INSERT_ID()";
    }

    /**
     * Reads the highest ticket, or 0 if none.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectSequenceLastValue(String)
     */
    @Override
    public String selectSequenceLastValue(String sequenceName) {
        return "SELECT COALESCE(MAX(id), 0) FROM " + nativeSequenceName(sequenceName);
    }

    /**
     * Inserts a ticket with the bound value, advancing AUTO_INCREMENT.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#advanceSequenceValue(String)
     */
    @Override
    public String advanceSequenceValue(String sequenceName) {
        return "INSERT IGNORE INTO " + nativeSequenceName(sequenceName) + " (id) VALUES (?)";
    }

    /**
     * Deletes ticket rows below the bound value.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteSequenceTicketsBelow(String)
     */
    @Override
    public String deleteSequenceTicketsBelow(String sequenceName) {
        return "DELETE FROM " + nativeSequenceName(sequenceName) + " WHERE id < ?";
    }

}