package io.apicurio.registry.storage.impl.sql;

/**
 * MS SQL Server implementation of the SQL statements interface. Provides sql statements that are specific to
 * MS SQL Server, where applicable.
 */
public class SQLServerSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     */
    public SQLServerSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "mssql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("Violation of PRIMARY KEY constraint")
                || error.getMessage().contains("Violation of UNIQUE KEY constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("conflicted with the FOREIGN KEY constraint");
    }

    @Override
    public String upsertBranch() {
        return """
                MERGE INTO branches AS target
                USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                ON (target.groupId = source.groupId AND target.artifactId = source.artifactId AND target.branchId = source.branchId)
                WHEN NOT MATCHED THEN
                INSERT (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (source.groupId, source.artifactId, source.branchId, source.description, source.systemDefined, source.owner, source.createdOn, source.modifiedBy, source.modifiedOn);
                """;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return """
                MERGE INTO sequences AS target
                USING (VALUES  (?)) AS source (seqName)
                ON (target.seqName = source.seqName)
                WHEN MATCHED THEN
                UPDATE SET seqValue = target.seqValue + 1
                WHEN NOT MATCHED THEN
                INSERT (seqName, seqValue)
                VALUES (source.seqName, 1)
                OUTPUT INSERTED.seqValue;
                """;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return """
                MERGE INTO sequences AS target
                USING (VALUES (?, ?)) AS source (seqName, seqValue)
                ON (target.seqName = source.seqName)
                WHEN MATCHED THEN
                UPDATE SET seqValue = ?
                WHEN NOT MATCHED THEN
                INSERT (seqName, seqValue)
                VALUES (source.seqName, source.seqValue)
                OUTPUT INSERTED.seqValue;
                """;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactIds()
     */
    @Override
    public String selectArtifactIds() {
        return "SELECT TOP (?) artifactId FROM artifacts ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     */
    @Override
    public String selectGroups() {
        // TODO pagination?
        return "SELECT TOP (?) * FROM groups ORDER BY groupId ASC";
    }

    @Override
    public String selectBranchTip() {
        return "SELECT bv.groupId, bv.artifactId, bv.version FROM branch_versions bv "
                + "WHERE bv.groupId = ? AND bv.artifactId = ? AND bv.branchId = ? "
                + "ORDER BY bv.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
    }

    @Override
    public String selectBranchTipFilteredByState() {
        return "SELECT bv.groupId, bv.artifactId, bv.version FROM branch_versions bv "
                + "JOIN versions v ON bv.groupId = v.groupId AND bv.artifactId = v.artifactId AND bv.version = v.version "
                + "WHERE bv.groupId = ? AND bv.artifactId = ? AND bv.branchId = ? AND v.state IN (?) "
                + "ORDER BY bv.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
    }

    @Override
    public String selectTableTemplate(String columns, String tableName, String alias, String whereClause,
            String orderBy) {
        return "SELECT %s FROM %s %s %s %s OFFSET ? ROWS FETCH NEXT ? ROWS ONLY".formatted(columns, tableName,
                alias, whereClause, orderBy);
    }

    @Override
    public String deleteAllOrphanedContent() {
        return "DELETE FROM content WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = contentId )";
    }

    @Override
    public String selectArtifactVersionStateForUpdate() {
        return "SELECT v.state FROM versions v WITH (UPDLOCK, ROWLOCK)"
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?";
    }

    @Override
    public String createDataSnapshot() {
        throw new IllegalStateException("Snapshot creation is not supported for Sqlserver storage");
    }

    @Override
    public String restoreFromSnapshot() {
        throw new IllegalStateException("Restoring from snapshot is not supported for Sqlserver storage");
    }
}
