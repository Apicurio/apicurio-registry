package io.apicurio.registry.storage.impl.sql;

/**
 * MS SQL Server implementation of the SQL statements interface.  Provides sql statements that
 * are specific to MS SQL Server, where applicable.
 */
public class SQLServerSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     * @param config
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
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements.core.storage.jdbc.ISqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return String.join(" ",
                "MERGE INTO content AS target",
                "USING (VALUES (?, ?, ?, ?, ?)) AS source (contentId, canonicalHash, contentHash, content, refs)",
                "ON (target.contentHash = source.contentHash)",
                "WHEN NOT MATCHED THEN",
                    "INSERT (contentId, canonicalHash, contentHash, content, refs)",
                    "VALUES (source.contentId, source.canonicalHash, source.contentHash, source.content, source.refs);");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return String.join(" ",
                "MERGE INTO sequences AS target",
                "USING (VALUES  (?)) AS source (seq_name)",
                "ON (target.seq_name = source.seq_name)",
                "WHEN MATCHED THEN",
                    "UPDATE SET seq_value = target.seq_value + 1",
                "WHEN NOT MATCHED THEN",
                    "INSERT (seq_name, seq_value)",
                    "VALUES (source.seq_name, 1)",
                "OUTPUT INSERTED.seq_value;");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return String.join(" ",
                "MERGE INTO sequences AS target",
                "USING (VALUES (?, ?)) AS source (seq_name, seq_value)",
                "ON (target.seq_name = source.seq_name)",
                "WHEN MATCHED THEN",
                    "UPDATE SET seq_value = ?",
                "WHEN NOT MATCHED THEN",
                    "INSERT (seq_name, seq_value)",
                    "VALUES (source.seq_name, source.seq_value)",
                "OUTPUT INSERTED.seq_value;");
    }

    /**
     * @see SqlStatements#upsertContentReference()
     */
    @Override
    public String upsertContentReference() {
        return String.join(" ",
                "MERGE INTO content_references AS target",
                "USING (VALUES (?, ?, ?, ?, ?)) AS source (contentId, groupId, artifactId, version, name)",
                "ON (target.contentId = source.contentId AND target.name = source.name)",
                "WHEN NOT MATCHED THEN",
                "INSERT (contentId, groupId, artifactId, version, name)",
                "VALUES (source.contentId, source.groupId, source.artifactId, source.version, source.name);");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactIds()
     */
    @Override
    public String selectArtifactIds() {
        return "SELECT TOP (?) artifactId FROM artifacts ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactVersions()
     */
    @Override
    public String selectAllArtifactVersions() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.groupId = ? AND a.artifactId = ? "
                + "ORDER BY v.globalId ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return "SELECT TOP (?) * FROM groups "
                + "ORDER BY groupId ASC";
    }


    @Override
    public String selectArtifactBranchTip() {
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? " +
                "ORDER BY ab.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
    }


    @Override
    public String selectArtifactBranchTipNotDisabled() {
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab " +
                "JOIN versions v ON ab.groupId = v.groupId AND ab.artifactId = v.artifactId AND ab.version = v.version " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? AND v.state != 'DISABLED' " +
                "ORDER BY ab.branchOrder DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY";
    }
}
