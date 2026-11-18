package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;

/**
 * Dialect-specific operations on the native ID sequences (PostgreSQL and SQL Server sequences,
 * MySQL AUTO_INCREMENT ticket tables). None of these operations lock anything until the caller's
 * transaction commits. Stateless, so it can be used both at runtime and from DB upgraders.
 */
public class SqlSequenceOperations {

    private final SqlStatements sqlStatements;

    /**
     * @param sqlStatements statements for the active database dialect (not H2)
     */
    public SqlSequenceOperations(SqlStatements sqlStatements) {
        this.sqlStatements = sqlStatements;
    }

    /**
     * Allocates the next value of the given sequence.
     */
    public long next(Handle handle, String sequenceName) {
        if (isMysql()) {
            handle.createUpdate(sqlStatements.nextSequenceValue(sequenceName)).execute();
            return handle.createQuery(sqlStatements.selectLastInsertedSequenceValue())
                    .mapTo(Long.class).one();
        }
        return handle.createQuery(sqlStatements.nextSequenceValue(sequenceName)).mapTo(Long.class).one();
    }

    /**
     * Returns the last value issued by the given sequence, or 0 if it never issued one.
     */
    public long lastValue(Handle handle, String sequenceName) {
        return handle.createQuery(sqlStatements.selectSequenceLastValue(sequenceName))
                .mapTo(Long.class).one();
    }

    /**
     * Moves the sequence forward so that the next value is greater than {@code target}. Does nothing
     * if the sequence is already at or beyond {@code target}.
     */
    public void advanceTo(Handle handle, String sequenceName, long target) {
        long last = lastValue(handle, sequenceName);
        if (target <= last) {
            return;
        }
        String sql = sqlStatements.advanceSequenceValue(sequenceName);
        switch (sqlStatements.dbType()) {
            case "postgresql" -> handle.createQuery(sql).bind(0, target).mapTo(Long.class).one();
            case "mssql" -> handle.createUpdate(sql).bind(0, target - last).execute();
            case "mysql" -> handle.createUpdate(sql).bind(0, target).execute();
            default -> throw new UnsupportedOperationException(
                    "Native sequences are not used for " + sqlStatements.dbType());
        }
    }

    /**
     * Deletes all but the newest MySQL ticket row of the given sequence. The newest row is kept
     * because MySQL 5.7 recomputes AUTO_INCREMENT from MAX(id) on restart. No-op on other dialects.
     */
    public void pruneTickets(Handle handle, String sequenceName) {
        if (!isMysql()) {
            return;
        }
        long last = lastValue(handle, sequenceName);
        if (last > 0) {
            handle.createUpdate(sqlStatements.deleteSequenceTicketsBelow(sequenceName))
                    .bind(0, last).execute();
        }
    }

    private boolean isMysql() {
        return "mysql".equals(sqlStatements.dbType());
    }
}
