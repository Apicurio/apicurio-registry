package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository handling sequence operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 *
 * H2 (and as a result KafkaSQL) uses static in-memory atomic counters. All other dialects
 * allocate IDs from native sequences (PostgreSQL, SQL Server) or AUTO_INCREMENT ticket tables
 * (MySQL), none of which hold a lock until the allocating transaction commits.
 */
public class SqlSequenceRepository {

    public static final String GLOBAL_ID_SEQUENCE = "globalId";
    public static final String CONTENT_ID_SEQUENCE = "contentId";
    public static final String COMMENT_ID_SEQUENCE = "commentId";

    // Sequence counters - only used for H2 in-memory (and as a result KafkaSQL)
    private static final Map<String, AtomicLong> sequenceCounters = new HashMap<>();
    static {
        sequenceCounters.put(GLOBAL_ID_SEQUENCE, new AtomicLong(0));
        sequenceCounters.put(CONTENT_ID_SEQUENCE, new AtomicLong(0));
        sequenceCounters.put(COMMENT_ID_SEQUENCE, new AtomicLong(0));
    }

    private final Logger log;

    private final SqlStatements sqlStatements;

    private final HandleFactory handles;

    private final SqlSequenceOperations operations;

    public SqlSequenceRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.operations = new SqlSequenceOperations(sqlStatements);
    }

    /**
     * Get next global ID.
     */
    public long nextGlobalId() {
        return handles.withHandleNoException(this::nextGlobalIdRaw);
    }

    /**
     * Get next global ID using an existing handle.
     */
    public long nextGlobalIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, GLOBAL_ID_SEQUENCE);
    }

    /**
     * Get next content ID.
     */
    public long nextContentId() {
        return handles.withHandleNoException(this::nextContentIdRaw);
    }

    /**
     * Get next content ID using an existing handle.
     */
    public long nextContentIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, CONTENT_ID_SEQUENCE);
    }

    /**
     * Get next comment ID.
     */
    public long nextCommentId() {
        return handles.withHandleNoException(this::nextCommentIdRaw);
    }

    /**
     * Get next comment ID using an existing handle.
     */
    public long nextCommentIdRaw(Handle handle) {
        return nextSequenceValueRaw(handle, COMMENT_ID_SEQUENCE);
    }

    /**
     * Get next sequence value using an existing handle.
     */
    private long nextSequenceValueRaw(Handle handle, String sequenceName) {
        if (isH2()) {
            return sequenceCounters.get(sequenceName).incrementAndGet();
        }
        return operations.next(handle, sequenceName);
    }

    /**
     * Reset global ID sequence.
     */
    public void resetGlobalId() {
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, GLOBAL_ID_SEQUENCE, sqlStatements.selectMaxGlobalId());
        });
    }

    /**
     * Reset content ID sequence.
     */
    public void resetContentId() {
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, CONTENT_ID_SEQUENCE, sqlStatements.selectMaxContentId());
        });
    }

    /**
     * Reset comment ID sequence.
     */
    public void resetCommentId() {
        handles.withHandleNoException(handle -> {
            resetSequenceRaw(handle, COMMENT_ID_SEQUENCE, sqlStatements.selectMaxVersionCommentId());
        });
    }

    private void resetSequenceRaw(Handle handle, String sequenceName, String sqlMaxIdFromTable) {
        Optional<Long> maxIdTable = handle.createQuery(sqlMaxIdFromTable).mapTo(Long.class).findOne();
        if (maxIdTable.isEmpty()) {
            return;
        }
        long target = maxIdTable.orElseThrow();
        log.info("Resetting {} sequence", sequenceName);
        if (isH2()) {
            sequenceCounters.get(sequenceName).accumulateAndGet(target, Math::max);
        } else {
            operations.advanceTo(handle, sequenceName, target);
        }
        log.info("Successfully reset {} to at least {}", sequenceName, target);
    }

    /**
     * Initialize sequence counters (called during storage initialization for H2).
     */
    public void initializeSequenceCounters(Handle handle) {
        if (isH2()) {
            sequenceCounters.get(GLOBAL_ID_SEQUENCE).set(getMaxGlobalIdRaw(handle));
            sequenceCounters.get(CONTENT_ID_SEQUENCE).set(getMaxContentIdRaw(handle));
            sequenceCounters.get(COMMENT_ID_SEQUENCE).set(getMaxVersionCommentIdRaw(handle));
        }
    }

    /**
     * Get max global ID from the database.
     */
    public long getMaxGlobalIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxGlobalId());
    }

    /**
     * Get max content ID from the database.
     */
    public long getMaxContentIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxContentId());
    }

    /**
     * Get max version comment ID from the database.
     */
    public long getMaxVersionCommentIdRaw(Handle handle) {
        return getMaxIdRaw(handle, sqlStatements.selectMaxVersionCommentId());
    }

    private long getMaxIdRaw(Handle handle, String sql) {
        Optional<Long> maxIdTable = handle.createQuery(sql)
                .mapTo(Long.class)
                .findOne();
        return maxIdTable.orElse(1L);
    }

    private boolean isH2() {
        return sqlStatements.dbType().equals("h2");
    }
}
