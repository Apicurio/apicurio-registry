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
}