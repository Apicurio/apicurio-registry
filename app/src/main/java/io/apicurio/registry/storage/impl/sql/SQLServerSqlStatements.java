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
        return error.getMessage().contains("Violation of PRIMARY KEY constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("conflicted with the FOREIGN KEY constraint");
    }

    /**
     * @see SqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts'";
    }

    @Override
    public String upsertBranch() {
        return """
                MERGE INTO branches AS target
                USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                ON (target.groupId = source.groupId AND target.artifactId = source.artifactId AND target.branchId = source.branchId)
                WHEN NOT MATCHED THEN
                INSERT (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (source.groupId, source.artifactId, source.branchId, source.description, source.systemDefined, source.owner, source.createdOn, source.modifiedBy, source.modifiedOn)
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
                OUTPUT INSERTED.seqValue
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
                OUTPUT INSERTED.seqValue
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
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab "
                + "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? "
                + "ORDER BY ab.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
    }

    @Override
    public String selectBranchTipNotDisabled() {
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab "
                + "JOIN versions v ON ab.groupId = v.groupId AND ab.artifactId = v.artifactId AND ab.version = v.version "
                + "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? AND v.state != 'DISABLED' "
                + "ORDER BY ab.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
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
