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
        // Use a PostgreSQL *transaction-level* advisory lock with a fixed key derived from
        // "apicurio-init" (1886352239). The whole initialize()/upgrade() callback runs inside one
        // handle, which commits only after the callback returns; a session-level lock
        // (pg_advisory_lock/pg_advisory_unlock) is released by the explicit unlock call in that
        // callback's `finally`, which runs *before* that commit. A second replica can then acquire
        // the lock, see the still-uncommitted DDL as "not initialized" via a read in the same
        // READ COMMITTED snapshot it just took the lock under, and race to create the schema too
        // (seen as a duplicate-key error on pg_type, not "relation already exists" -- the signature
        // of two concurrent CREATE TABLEs). pg_advisory_xact_lock is held until the transaction
        // commits or rolls back, so the lock itself now covers the commit; see the no-op
        // releaseInitLock() below.
        return "SELECT pg_advisory_xact_lock(1886352239), 1";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        // No-op: a transaction-level advisory lock (acquireInitLock() above) has no explicit
        // unlock function and is released automatically when the enclosing transaction commits
        // or rolls back, which is exactly the point at which it must still be held.
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
