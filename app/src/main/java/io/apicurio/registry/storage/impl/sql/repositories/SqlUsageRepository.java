package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.ConsumerVersionEntryDto;
import io.apicurio.registry.storage.dto.DeprecationReadinessDto;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.jdb.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;

public class SqlUsageRepository {

    private static final Logger log = LoggerFactory.getLogger(SqlUsageRepository.class);

    private static final String CLIENT_ID_COLUMN = "clientId";
    private static final int WRITE_ATTEMPTS = 3;
    /** 200 rows keep each transaction short, so it holds its row locks only briefly. */
    private static final int CHUNK_SIZE = 200;

    /** One UTC day in milliseconds; usage rows are keyed by {@link #usageDay(long)}. */
    public static final long DAY_MS = Duration.ofDays(1).toMillis();

    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlUsageRepository(HandleFactory handles, SqlStatements sqlStatements) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
    }

    /**
     * Rows hold whole UTC days, so the cutoff is rounded down to the start of its day: a day row is deleted
     * only when every event it can hold is older than that day. Events from the cutoff's own day survive
     * until the first run after midnight UTC, and the job runs once per aggregation interval, so usage is
     * kept for up to one day plus one aggregation interval past the retention.
     */
    public void deleteOldUsageEvents(long cutoffTimestamp) {
        long cutoffDay = usageDay(cutoffTimestamp);
        handles.withRetryingHandle(
                handle -> handle.createUpdate(sqlStatements.deleteOldSchemaUsageDays()).bind(0, cutoffDay).execute(),
                this::isRetryable, WRITE_ATTEMPTS);
    }

    public void recordUsageEvent(SchemaUsageEventDto event) {
        recordUsageEvents(List.of(event));
    }

    /**
     * Folds the events into one row per day key, sorts the rows once in {@link DailyKey#LOCK_ORDER} and adds
     * them with the dialect's upsert in chunks of at most CHUNK_SIZE rows, one transaction per chunk, in that
     * order. Rows that already exist are therefore locked in one order by every writer; races on new keys
     * are covered by the retry.
     * <p>
     * A chunk that still fails after its retries with a data error ({@link SqlStatements#findDataError})
     * costs only its own rows, and the remaining chunks are still written. Any other failure, for example a
     * lost connection, stops the loop at once, so an outage does not make every chunk wait out the connection
     * backoff. The loop also stops at the first failure when the thread is interrupted, or when the write
     * joined an enclosing transaction, which that failure has already made rollback-only. The exception
     * thrown names the rows that were not stored, with the SQLState and vendor code of each data error. The
     * deciding failure, the one that stopped the loop or else the last data error, is its cause, earlier
     * failures are suppressed, and its range also carries the reason: the message of the SQLException that
     * classified it, or the first line of its own message. A database failure ({@link
     * SqlStatements#isDatabaseFailure}) is thrown as a RuntimeSqlException, which KafkaSQL logs at WARN when
     * it cannot apply a journal record; any other failure as a RegistryStorageException. Chunks that
     * committed stay committed, and no caller writes them again.
     */
    public void recordUsageEvents(List<SchemaUsageEventDto> events) {
        List<DailyUsage> rows = rollUp(events);
        boolean joined = handles.isInHandle();
        RuntimeException failure = null;
        List<RuntimeException> earlier = new ArrayList<>();
        StringJoiner notStored = new StringJoiner("; ");
        String range = null;
        String codes = null;
        String reason = null;
        for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
            List<DailyUsage> chunk = rows.subList(from, Math.min(from + CHUNK_SIZE, rows.size()));
            try {
                handles.withRetryingHandle(handle -> upsert(handle, chunk), this::isRetryable, WRITE_ATTEMPTS);
            } catch (RuntimeException e) {
                if (failure != null) {
                    earlier.add(failure);
                    notStored.add(describe(range, codes, null));
                }
                failure = e;
                Optional<SQLException> dataError = sqlStatements.findDataError(e);
                codes = dataError.map(d -> "SQLState " + d.getSQLState() + ", vendor code " + d.getErrorCode())
                        .orElse(null);
                reason = firstLine(dataError.isPresent() ? dataError.get() : e);
                boolean stop = joined || Thread.currentThread().isInterrupted() || dataError.isEmpty();
                int to = stop ? rows.size() : from + chunk.size();
                range = joined
                        ? "all " + rows.size() + " usage rows were not stored (the enclosing transaction is rolled"
                                + " back)"
                        : "usage rows " + (from + 1) + " to " + to + " of " + rows.size() + " were not stored";
                if (stop) {
                    break;
                }
                if (to < rows.size()) {
                    log.warn("{}; continuing with the rest.", describe(range, codes, reason));
                }
            }
        }
        if (failure != null) {
            notStored.add(describe(range, codes, reason));
            String message = notStored.toString();
            RuntimeException thrown = sqlStatements.isDatabaseFailure(failure)
                    ? new RuntimeSqlException(message, failure)
                    : new RegistryStorageException(message, failure);
            earlier.forEach(thrown::addSuppressed);
            throw thrown;
        }
    }

    /** The rows text, then the codes and the reason in parentheses when there are any. */
    private static String describe(String rows, String codes, String reason) {
        String detail = codes == null ? reason : reason == null ? codes : codes + ": " + reason;
        return detail == null ? rows : rows + " (" + detail + ")";
    }

    /**
     * The first non-blank line of the message, or the class name when there is none. Called with the
     * SQLException that classified a data error, so the reason is the database's own text and not a batch
     * wrapper's, which on PostgreSQL copies the statement and its bound values.
     */
    private static String firstLine(Throwable error) {
        return Objects.requireNonNullElse(error.getMessage(), "").lines().filter(line -> !line.isBlank())
                .findFirst().map(String::strip).orElse(error.getClass().getName());
    }

    /**
     * A deadlock or serialization failure is rolled back and may succeed when run again, on every dialect.
     * A primary key violation is retried only where the upsert is not atomic (H2): the row now exists, so
     * the retry takes the update branch.
     */
    boolean isRetryable(RuntimeException e) {
        return sqlStatements.isTransientConflict(e) || sqlStatements.isUpsertKeyRace(e);
    }

    private void upsert(Handle handle, Collection<DailyUsage> rows) {
        Update upsert = handle.createUpdate(sqlStatements.upsertSchemaUsageDaily());
        for (DailyUsage row : rows) {
            upsert.bind(0, row.key().globalId())
                    .bind(1, row.key().contentId())
                    .bind(2, row.key().clientId())
                    .bind(3, row.key().operation())
                    .bind(4, row.key().usageDay())
                    .bind(5, row.fetchCount())
                    .bind(6, row.firstEventTimestamp())
                    .bind(7, row.lastEventTimestamp())
                    .addBatch();
        }
        upsert.executeBatch();
    }

    /** The UTC epoch day of a timestamp in milliseconds, computed here so that no SQL dialect has to. */
    public static long usageDay(long timestamp) {
        return Math.floorDiv(timestamp, DAY_MS);
    }

    static List<DailyUsage> rollUp(List<SchemaUsageEventDto> events) {
        Map<DailyKey, DailyUsage> rows = new TreeMap<>(DailyKey.LOCK_ORDER);
        for (SchemaUsageEventDto event : events) {
            DailyKey key = new DailyKey(event.getGlobalId(), event.getContentId(),
                    Objects.requireNonNull(event.getClientId(), "usage event without a client id"),
                    Objects.requireNonNull(event.getOperation(), "usage event without an operation"),
                    usageDay(event.getEventTimestamp()));
            DailyUsage single = new DailyUsage(key, 1, event.getEventTimestamp(), event.getEventTimestamp());
            rows.merge(key, single, DailyUsage::plus);
        }
        return List.copyOf(rows.values());
    }

    record DailyKey(long globalId, long contentId, String clientId, String operation, long usageDay) {

        /**
         * Compares ignoring case and trailing spaces. This is not a model of the database collations
         * (SQL Server's NVARCHAR folding also covers width, kana and other characters that Java does not
         * reproduce). It is enough because UsageTelemetryFilter admits only client ids made of
         * {@code [a-zA-Z0-9._-]} and operations of trimmed printable ASCII, so the only spellings that can
         * reach the table as one row are ASCII case variants and trailing spaces. If some other input reached
         * the table, for example a record replayed from an older journal, a deadlock between two exotic
         * spellings of one row is resolved by the write retry. In production one flush per node writes and
         * the filter trims operations, so this folding mainly protects direct multi-threaded callers and
         * tests; it is kept because it costs nothing.
         */
        private static final Comparator<String> FOLDED = DailyKey::compareFolded;

        /**
         * One order that every writer uses, so concurrent batches reach the rows they share in the same
         * sequence. It is not the database's index order. Client id and operation are compared {@link #FOLDED}
         * first, so keys that a case-insensitive, space-padding collation treats as one row are adjacent, and
         * such a row keeps its place in the order whichever spelling a batch uses. The exact-string comparisons
         * at the end only separate those adjacent keys.
         */
        static final Comparator<DailyKey> LOCK_ORDER = Comparator.comparingLong(DailyKey::globalId)
                .thenComparingLong(DailyKey::contentId)
                .thenComparing(DailyKey::clientId, FOLDED)
                .thenComparing(DailyKey::operation, FOLDED)
                .thenComparingLong(DailyKey::usageDay)
                .thenComparing(DailyKey::clientId)
                .thenComparing(DailyKey::operation);

        /**
         * String.CASE_INSENSITIVE_ORDER over the strings without their trailing U+0020. substring(0, length)
         * returns the same String when there is nothing to trim, so the common case allocates nothing.
         * The JDK order matches the old character loop for the ASCII inputs UsageTelemetryFilter admits;
         * astral code points fold differently, and such inputs fall to the write retry instead.
         */
        private static int compareFolded(String left, String right) {
            return String.CASE_INSENSITIVE_ORDER.compare(left.substring(0, lengthWithoutTrailingSpaces(left)),
                    right.substring(0, lengthWithoutTrailingSpaces(right)));
        }

        /** Space padding compares with U+0020 only, so other trailing whitespace stays significant. */
        private static int lengthWithoutTrailingSpaces(String value) {
            int end = value.length();
            while (end > 0 && value.charAt(end - 1) == ' ') {
                end--;
            }
            return end;
        }
    }

    record DailyUsage(DailyKey key, long fetchCount, long firstEventTimestamp, long lastEventTimestamp) {

        DailyUsage plus(DailyUsage other) {
            return new DailyUsage(key, fetchCount + other.fetchCount,
                    Math.min(firstEventTimestamp, other.firstEventTimestamp),
                    Math.max(lastEventTimestamp, other.lastEventTimestamp));
        }
    }

    public List<SchemaUsageSummaryDto> getArtifactUsageMetrics(String groupId, String artifactId) {
        List<ClientVersionUsage> rows = handles.withHandleNoException(handle -> {
            return handle
                    .createQuery(sqlStatements.selectArtifactUsageMetrics())
                    .bind(0, RegistryContentUtils.normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .map(rs -> new ClientVersionUsage(rs.getString("version"), rs.getLong("globalId"),
                            rs.getString(CLIENT_ID_COLUMN), rs.getLong("fetches"), rs.getLong("firstFetchedOn"),
                            rs.getLong("lastFetchedOn")))
                    .list();
        });
        return foldByVersion(rows);
    }

    /**
     * Rows arrive ordered by version order and then client, one per (version, client). Folding them here
     * keeps the SQL free of per-database string aggregation.
     */
    static List<SchemaUsageSummaryDto> foldByVersion(List<ClientVersionUsage> rows) {
        Map<Long, Fold> folds = new LinkedHashMap<>();
        for (ClientVersionUsage row : rows) {
            folds.computeIfAbsent(row.globalId(), id -> new Fold(row.version(), id)).add(row);
        }
        return folds.values().stream().map(Fold::toDto).toList();
    }

    private static final class Fold {
        private final String version;
        private final long globalId;
        private final StringJoiner clients = new StringJoiner(",");
        private long totalFetches;
        private int uniqueClients;
        private long firstFetchedOn = Long.MAX_VALUE;
        private long lastFetchedOn = Long.MIN_VALUE;

        Fold(String version, long globalId) {
            this.version = version;
            this.globalId = globalId;
        }

        void add(ClientVersionUsage row) {
            clients.add(row.clientId());
            totalFetches += row.fetches();
            uniqueClients++;
            firstFetchedOn = Math.min(firstFetchedOn, row.firstFetchedOn());
            lastFetchedOn = Math.max(lastFetchedOn, row.lastFetchedOn());
        }

        SchemaUsageSummaryDto toDto() {
            return SchemaUsageSummaryDto.builder().version(version).globalId(globalId)
                    .totalFetches(totalFetches).uniqueClients(uniqueClients).firstFetchedOn(firstFetchedOn)
                    .lastFetchedOn(lastFetchedOn).clientList(clients.toString()).build();
        }
    }

    record ClientVersionUsage(String version, long globalId, String clientId, long fetches,
            long firstFetchedOn, long lastFetchedOn) {
    }

    public UsageSummaryCountsDto getUsageSummaryCounts(long nowMs, long activeMs, long staleMs) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectUsageSummaryCounts())
                    .bind(0, nowMs).bind(1, activeMs)
                    .bind(2, nowMs).bind(3, activeMs).bind(4, nowMs).bind(5, staleMs)
                    .bind(6, nowMs).bind(7, staleMs)
                    .map(rs -> UsageSummaryCountsDto.builder()
                            .active(rs.getInt("active"))
                            .stale(rs.getInt("stale"))
                            .dead(rs.getInt("dead"))
                            .build())
                    .one();
        });
    }

    public List<ConsumerVersionEntryDto> getConsumerVersionHeatmap(String groupId, String artifactId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectConsumerVersionHeatmap())
                    .bind(0, RegistryContentUtils.normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .map(rs -> ConsumerVersionEntryDto.builder()
                            .clientId(rs.getString(CLIENT_ID_COLUMN))
                            .globalId(rs.getLong("globalId"))
                            .version(rs.getString("version"))
                            .versionOrder(rs.getInt("versionOrder"))
                            .fetchCount(rs.getLong("fetchCount"))
                            .build())
                    .list();
        });
    }

    public List<DeprecationReadinessDto> getDeprecationReadiness(String groupId, String artifactId,
                                                                  String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectDeprecationReadiness())
                    .bind(0, RegistryContentUtils.normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .map(rs -> DeprecationReadinessDto.builder()
                            .clientId(rs.getString(CLIENT_ID_COLUMN))
                            .lastFetched(rs.getLong("lastFetched"))
                            .fetchCount(rs.getLong("fetchCount"))
                            .build())
                    .list();
        });
    }
}
