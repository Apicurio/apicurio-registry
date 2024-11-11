package io.apicurio.registry.storage.impl.sql;

/**
 * PostgreSQL implementation of the sql statements interface. Provides sql statements that are specific to
 * PostgreSQL, where applicable.
 */
public class PostgreSQLSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     */
    public PostgreSQLSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "postgresql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("violates unique constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("violates foreign key constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, 1) ON CONFLICT (seqName) DO UPDATE SET seqValue = sequences.seqValue + 1 RETURNING seqValue";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, ?) ON CONFLICT (seqName) DO UPDATE SET seqValue = ?";
    }

    @Override
    public String upsertBranch() {
        return """
                INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (groupId, artifactId, branchId) DO NOTHING
                """;
    }

    @Override
    public String createDataSnapshot() {
        throw new IllegalStateException("Snapshot creation is not supported for Postgresql storage");
    }

    @Override
    public String restoreFromSnapshot() {
        throw new IllegalStateException("Restoring from snapshot is not supported for Postgresql storage");
    }

    @Override
    public String createOutboxEvent() {
        return """
                INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """;
    }

}