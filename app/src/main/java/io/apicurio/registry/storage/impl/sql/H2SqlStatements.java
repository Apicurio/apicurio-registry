package io.apicurio.registry.storage.impl.sql;

import java.util.Set;

/**
 * H2 implementation of the sql statements interface. Provides sql statements that are specific to H2, where
 * applicable.
 */
public class H2SqlStatements extends CommonSqlStatements {

    private static final Set<Integer> TRANSIENT_CONFLICT_ERROR_CODES = Set.of(90131);
    /** org.h2.api.ErrorCode.DUPLICATE_KEY_1. */
    private static final int DUPLICATE_KEY_ERROR_CODE = 23505;

    /**
     * Constructor.
     */
    public H2SqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "h2";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage() != null && error.getMessage().contains("primary key violation");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage() != null
                && error.getMessage().contains("Referential integrity constraint violation");
    }

    /** Concurrent update (90131); H2 reports a detected deadlock as SQLState 40001. */
    @Override
    protected Set<Integer> transientConflictErrorCodes() {
        return TRANSIENT_CONFLICT_ERROR_CODES;
    }

    /**
     * H2's MERGE does not lock a key it has not found, so two writers of a new key can both take the insert
     * branch, and the later one fails on the primary key with DUPLICATE_KEY_1 (23505). Classified by that
     * error code, never by message text. In production one flush per node writes, so the race mainly
     * concerns direct multi-threaded callers and tests; the retry is kept so they stay correct.
     */
    @Override
    public boolean isUpsertKeyRace(Exception error) {
        return anySqlException(error, e -> e.getErrorCode() == DUPLICATE_KEY_ERROR_CODE);
    }

    /**
     * Adds a fetch count to the day row of one (globalId, contentId, clientId, operation) key and widens its
     * first and last event timestamps, inserting the row when the key has none yet for that day.
     */
    @Override
    public String upsertSchemaUsageDaily() {
        return "MERGE INTO schema_usage_daily AS target "
                + "USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?)) AS source (globalId, contentId, clientId, operation, usageDay, fetchCount, firstEventTimestamp, lastEventTimestamp) "
                + "ON (target.globalId = source.globalId AND target.contentId = source.contentId AND target.clientId = source.clientId "
                + "AND target.operation = source.operation AND target.usageDay = source.usageDay) "
                + "WHEN MATCHED THEN UPDATE SET fetchCount = target.fetchCount + source.fetchCount, "
                + "firstEventTimestamp = LEAST(target.firstEventTimestamp, source.firstEventTimestamp), "
                + "lastEventTimestamp = GREATEST(target.lastEventTimestamp, source.lastEventTimestamp) "
                + "WHEN NOT MATCHED THEN INSERT (globalId, contentId, clientId, operation, usageDay, fetchCount, firstEventTimestamp, lastEventTimestamp) "
                + "VALUES (source.globalId, source.contentId, source.clientId, source.operation, source.usageDay, source.fetchCount, source.firstEventTimestamp, source.lastEventTimestamp)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_name = 'APICURIO'";
    }

    @Override
    public String isDatabaseSchemaInitialized() {
        return "SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_schema = ? AND table_name = 'APICURIO'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        throw new RuntimeException("Not applicable when using H2 as the database kind.");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        throw new RuntimeException("Not applicable when using H2 as the database kind.");
    }

    @Override
    public String insertSequenceValue() {
        throw new RuntimeException("Not applicable when using H2 as the database kind.");
    }

    @Override
    public String selectCurrentSequenceValue() {
        throw new RuntimeException("Not applicable when using H2 as the database kind.");
    }

    @Override
    public String createDataSnapshot() {
        return "SCRIPT TO ?";
    }

    @Override
    public String createDataSnapshot(String location) {
        if (location != null && location.endsWith(COMPRESSED_SNAPSHOT_EXTENSION)) {
            return "SCRIPT TO ? COMPRESSION GZIP";
        }
        return createDataSnapshot();
    }

    @Override
    public String restoreFromSnapshot() {
        return "RUNSCRIPT FROM ?";
    }

    @Override
    public String restoreFromSnapshot(String location) {
        if (location != null && location.endsWith(COMPRESSED_SNAPSHOT_EXTENSION)) {
            return "RUNSCRIPT FROM ? COMPRESSION GZIP";
        }
        return restoreFromSnapshot();
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#acquireInitLock()
     */
    @Override
    public String acquireInitLock() {
        // H2 is only used in single-instance mode, so no distributed locking needed
        // Return a no-op query that always succeeds
        return "SELECT 1";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#releaseInitLock()
     */
    @Override
    public String releaseInitLock() {
        // H2 is only used in single-instance mode, so no distributed locking needed
        // Return a no-op query that always succeeds
        return "SELECT 1";
    }
}
