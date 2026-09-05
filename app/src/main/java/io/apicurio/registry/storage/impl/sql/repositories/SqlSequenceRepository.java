package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository handling sequence operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 *
 * Note: H2 uses in-memory atomic counters for sequences instead of database
 * sequences, which is why this repository maintains static counters.
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

    private final int blockSize;

    private final Map<String, SequenceBlock> blocks = new ConcurrentHashMap<>();

    public SqlSequenceRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this(handles, sqlStatements, log, 1);
    }

    public SqlSequenceRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log,
            int blockSize) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.blockSize = Math.max(1, blockSize);
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
        if (blockSize > 1) {
            return blocks.computeIfAbsent(sequenceName, SequenceBlock::new).next();
        }
        return nextSequenceValueFromDatabase(handle, sequenceName);
    }

    private long nextSequenceValueFromDatabase(Handle handle, String sequenceName) {
        if (isMysql()) {
            handle.createUpdate(sqlStatements.getNextSequenceValue())
                    .bind(0, sequenceName)
                    .execute();
            return handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                    .bind(0, sequenceName)
                    .mapTo(Long.class)
                    .one();
        } else {
            return handle.createQuery(sqlStatements.getNextSequenceValue())
                    .bind(0, sequenceName)
                    .mapTo(Long.class)
                    .one();
        }
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
        SequenceBlock discarded = blocks.remove(sequenceName);
        if (discarded != null) {
            discarded.invalidate();
        }
        Optional<Long> maxIdTable = handle.createQuery(sqlMaxIdFromTable)
                .mapTo(Long.class)
                .findOne();

        Optional<Long> current;
        if (isH2()) {
            current = Optional.of(sequenceCounters.get(sequenceName).get());
        } else {
            current = handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                    .bind(0, sequenceName)
                    .mapTo(Long.class)
                    .findOne();
        }
        final Optional<Long> currentIdSeq = current;

        Optional<Long> maxId = maxIdTable.map(maxIdTableValue -> {
            if (currentIdSeq.isPresent()) {
                if (currentIdSeq.orElseThrow() > maxIdTableValue) {
                    return currentIdSeq.orElseThrow();
                }
            }
            return maxIdTableValue;
        });

        if (maxId.isPresent()) {
            log.info("Resetting {} sequence", sequenceName);
            long id = maxId.orElseThrow();

            if (isH2()) {
                sequenceCounters.get(sequenceName).set(id);
            } else {
                handle.createUpdate(sqlStatements.resetSequenceValue())
                        .bind(0, sequenceName)
                        .bind(1, id)
                        .bind(2, id)
                        .execute();
            }

            log.info("Successfully reset {} to {}", sequenceName, id);
        }
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

    private class SequenceBlock {

        private final String sequenceName;

        private long next = 1;

        private long end = 0;

        SequenceBlock(String sequenceName) {
            this.sequenceName = sequenceName;
        }

        synchronized void invalidate() {
            next = 1;
            end = 0;
        }

        synchronized long next() {
            if (next > end) {
                long reservedEnd = reserveBlock(sequenceName);
                next = reservedEnd - blockSize + 1;
                end = reservedEnd;
                log.debug("Reserved {} block [{}, {}]", sequenceName, next, end);
            }
            return next++;
        }
    }

    private long reserveBlock(String sequenceName) {
        return handles.withIsolatedHandleNoException(handle -> {
            if (isMysql()) {
                handle.createUpdate(sqlStatements.getNextSequenceValueBlock())
                        .bind(0, sequenceName)
                        .bind(1, (long) blockSize)
                        .bind(2, (long) blockSize)
                        .execute();
                return handle.createQuery(sqlStatements.selectCurrentSequenceValue())
                        .bind(0, sequenceName)
                        .mapTo(Long.class)
                        .one();
            }
            return handle.createQuery(sqlStatements.getNextSequenceValueBlock())
                    .bind(0, sequenceName)
                    .bind(1, (long) blockSize)
                    .bind(2, (long) blockSize)
                    .mapTo(Long.class)
                    .one();
        });
    }

    private boolean isH2() {
        return sqlStatements.dbType().equals("h2");
    }

    private boolean isPostgresql() {
        return sqlStatements.dbType().equals("postgresql");
    }

    private boolean isMysql() {
        return sqlStatements.dbType().equals("mysql");
    }
}
