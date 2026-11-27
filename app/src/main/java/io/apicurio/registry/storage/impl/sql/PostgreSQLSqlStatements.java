package io.apicurio.registry.storage.impl.sql;

/**
 * PostgreSQL implementation of the sql statements interface. Provides sql statements that are specific to
 * PostgreSQL, where applicable.
 */
public class PostgreSQLSqlStatements extends CommonSqlStatements {

    @Override
    public String insertContentIfAbsent() {
        return insertContent() + " ON CONFLICT (contentHash) DO NOTHING";
    }

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

    @Override
    public String upsertBranch() {
        return """
                INSERT INTO branches (groupId, artifactId, branchId, description, systemDefined, owner, createdOn, modifiedBy, modifiedOn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (groupId, artifactId, branchId) DO NOTHING
                """;
    }

    @Override
    public String upsertConfigProperty() {
        return "INSERT INTO config (propName, propValue, modifiedOn) VALUES (?, ?, ?) "
                + "ON CONFLICT (propName) DO UPDATE SET propValue = EXCLUDED.propValue, modifiedOn = EXCLUDED.modifiedOn";
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

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#acquireInitLock()
     */
    @Override
    public String acquireInitLock() {
        // Use PostgreSQL advisory locks with a fixed key derived from "apicurio-init"
        // Key: 1886352239 (hash of "apicurio-init")
        // This is a transaction-level lock that blocks until acquired and is released automatically
        // when the initialization transaction commits or rolls back, so another replica cannot
        // acquire it while the DDL is still uncommitted.
        // Note: pg_advisory_xact_lock() returns void, so select 1 from it to get a positive result
        return "SELECT 1 FROM pg_advisory_xact_lock(1886352239)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        // No-op: the transaction-level lock taken in acquireInitLock() is released on commit/rollback.
        return "SELECT 1";
    }


    /**
     * Uses the native PostgreSQL sequence.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#nextSequenceValue(String)
     */
    @Override
    public String nextSequenceValue(String sequenceName) {
        return "SELECT nextval('" + nativeSequenceName(sequenceName) + "')";
    }

    /**
     * Reads the last issued value, or 0 if none.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectSequenceLastValue(String)
     */
    @Override
    public String selectSequenceLastValue(String sequenceName) {
        return "SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM "
                + nativeSequenceName(sequenceName);
    }

    /**
     * Sets the sequence to the bound value. setval is absolute, so GREATEST keeps it from rewinding
     * past values already issued when the statement runs.
     *
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#advanceSequenceValue(String)
     */
    @Override
    public String advanceSequenceValue(String sequenceName) {
        String name = nativeSequenceName(sequenceName);
        return "SELECT setval('" + name + "', GREATEST(?, (SELECT last_value FROM " + name + ")), true)";
    }

}
