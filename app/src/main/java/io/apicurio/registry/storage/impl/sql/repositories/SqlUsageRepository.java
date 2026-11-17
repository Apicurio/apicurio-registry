package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.ConsumerVersionEntryDto;
import io.apicurio.registry.storage.dto.DeprecationReadinessDto;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlStatements;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class SqlUsageRepository {

    private static final String CLIENT_ID_COLUMN = "clientId";

    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlUsageRepository(HandleFactory handles, SqlStatements sqlStatements) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
    }

    public void deleteOldUsageEvents(long cutoffTimestamp) {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteOldSchemaUsageEvents())
                    .bind(0, cutoffTimestamp)
                    .execute();
            return null;
        });
    }

    public void recordUsageEvent(SchemaUsageEventDto event) {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.insertSchemaUsage())
                    .bind(0, event.getGlobalId())
                    .bind(1, event.getContentId())
                    .bind(2, event.getClientId())
                    .bind(3, event.getOperation())
                    .bind(4, event.getEventTimestamp())
                    .execute();
            return null;
        });
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
