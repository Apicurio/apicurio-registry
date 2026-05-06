package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.ConsumerVersionEntryDto;
import io.apicurio.registry.storage.dto.DeprecationReadinessDto;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import org.slf4j.Logger;

import java.util.List;

public class SqlUsageRepository {

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlUsageRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void recordUsageEvents(List<SchemaUsageEventDto> events) {
        handles.withHandle(handle -> {
            for (SchemaUsageEventDto event : events) {
                handle.createUpdate(sqlStatements.insertSchemaUsage())
                        .bind(0, event.getGlobalId())
                        .bind(1, event.getClientId())
                        .bind(2, event.getOperation())
                        .bind(3, event.getEventTimestamp())
                        .execute();
            }
            return null;
        });
    }

    public void aggregateUsageData() {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.deleteSchemaUsageSummary()).execute();
            handle.createUpdate(sqlStatements.insertSchemaUsageSummary()).execute();
            return null;
        });
    }

    public List<SchemaUsageSummaryDto> getArtifactUsageMetrics(String groupId, String artifactId) {
        return handles.withHandleNoException(handle -> {
            return handle
                    .createQuery(sqlStatements.selectArtifactUsageMetrics())
                    .bind(0, RegistryContentUtils.normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .map(rs -> SchemaUsageSummaryDto.builder()
                            .version(rs.getString("version"))
                            .globalId(rs.getLong("globalId"))
                            .totalFetches(rs.getLong("totalFetches"))
                            .uniqueClients(rs.getInt("uniqueClients"))
                            .firstFetchedOn(rs.getLong("firstFetchedOn"))
                            .lastFetchedOn(rs.getLong("lastFetchedOn"))
                            .clientList(rs.getString("clientList"))
                            .build())
                    .list();
        });
    }

    public UsageSummaryCountsDto getUsageSummaryCounts(long nowMs, long activeMs, long staleMs, long deadMs) {
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
                            .clientId(rs.getString("clientId"))
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
                            .clientId(rs.getString("clientId"))
                            .lastFetched(rs.getLong("lastFetched"))
                            .fetchCount(rs.getLong("fetchCount"))
                            .build())
                    .list();
        });
    }
}
