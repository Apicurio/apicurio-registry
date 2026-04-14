package io.apicurio.registry.storage.impl.sql.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.ContractRuleDtoMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

public class SqlContractRuleRepository {

    private static final String CATEGORY_DOMAIN = "DOMAIN";
    private static final String CATEGORY_MIGRATION = "MIGRATION";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;
    private final SqlVersionRepository versionRepository;

    public SqlContractRuleRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log,
            SqlVersionRepository versionRepository) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
        this.versionRepository = versionRepository;
    }

    public ContractRuleSetDto getArtifactContractRuleset(String groupId, String artifactId)
            throws RegistryStorageException {
        log.debug("Getting artifact contract ruleset for: {} {}", groupId, artifactId);
        return handles.withHandle(handle -> {
            List<Pair<String, ContractRuleDto>> rows = handle
                    .createQuery(sqlStatements.selectContractRulesByArtifact())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                    .map(rs -> Pair.of(rs.getString("ruleCategory"),
                            ContractRuleDtoMapper.instance.map(rs)))
                    .list();
            return buildRuleSet(rows);
        });
    }

    public void setArtifactContractRuleset(String groupId, String artifactId,
            ContractRuleSetDto ruleset) throws RegistryStorageException {
        log.debug("Setting artifact contract ruleset for: {} {}", groupId, artifactId);
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteContractRulesByArtifact())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();
            insertRules(handle, groupId, artifactId, null, ruleset);
        });
    }

    public void deleteArtifactContractRuleset(String groupId, String artifactId)
            throws RegistryStorageException {
        log.debug("Deleting artifact contract ruleset for: {} {}", groupId, artifactId);
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteContractRulesByArtifact())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();
        });
    }

    public ContractRuleSetDto getVersionContractRuleset(String groupId, String artifactId,
            String version) throws VersionNotFoundException, RegistryStorageException {
        log.debug("Getting version contract ruleset for: {} {} {}", groupId, artifactId, version);
        long globalId = resolveGlobalId(groupId, artifactId, version);
        return handles.withHandle(handle -> {
            List<Pair<String, ContractRuleDto>> rows = handle
                    .createQuery(sqlStatements.selectContractRulesByGlobalId())
                    .bind(0, globalId)
                    .map(rs -> Pair.of(rs.getString("ruleCategory"),
                            ContractRuleDtoMapper.instance.map(rs)))
                    .list();
            return buildRuleSet(rows);
        });
    }

    public void setVersionContractRuleset(String groupId, String artifactId, String version,
            ContractRuleSetDto ruleset) throws VersionNotFoundException, RegistryStorageException {
        log.debug("Setting version contract ruleset for: {} {} {}", groupId, artifactId, version);
        long globalId = resolveGlobalId(groupId, artifactId, version);
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteContractRulesByGlobalId()).bind(0, globalId)
                    .execute();
            insertRules(handle, groupId, artifactId, globalId, ruleset);
        });
    }

    public void deleteVersionContractRuleset(String groupId, String artifactId, String version)
            throws VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version contract ruleset for: {} {} {}", groupId, artifactId, version);
        long globalId = resolveGlobalId(groupId, artifactId, version);
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteContractRulesByGlobalId()).bind(0, globalId)
                    .execute();
        });
    }

    private long resolveGlobalId(String groupId, String artifactId, String version) {
        return versionRepository.getArtifactVersionMetaData(groupId, artifactId, version)
                .getGlobalId();
    }

    @SuppressWarnings("resource")
    private void insertRules(io.apicurio.registry.storage.impl.sql.jdb.Handle handle,
            String groupId, String artifactId, Long globalId, ContractRuleSetDto ruleset) {
        List<ContractRuleDto> domainRules = ruleset.getDomainRules();
        if (domainRules != null) {
            for (int i = 0; i < domainRules.size(); i++) {
                insertRule(handle, groupId, artifactId, globalId, CATEGORY_DOMAIN, i,
                        domainRules.get(i));
            }
        }
        List<ContractRuleDto> migrationRules = ruleset.getMigrationRules();
        if (migrationRules != null) {
            for (int i = 0; i < migrationRules.size(); i++) {
                insertRule(handle, groupId, artifactId, globalId, CATEGORY_MIGRATION, i,
                        migrationRules.get(i));
            }
        }
    }

    @SuppressWarnings("resource")
    private void insertRule(io.apicurio.registry.storage.impl.sql.jdb.Handle handle,
            String groupId, String artifactId, Long globalId, String category, int orderIndex,
            ContractRuleDto rule) {
        handle.createUpdate(sqlStatements.insertContractRule())
                .bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId)
                .bind(2, globalId)
                .bind(3, category)
                .bind(4, orderIndex)
                .bind(5, rule.getName())
                .bind(6, rule.getKind().name())
                .bind(7, rule.getType())
                .bind(8, rule.getMode().name())
                .bind(9, rule.getExpr())
                .bind(10, serializeParams(rule.getParams()))
                .bind(11, serializeTags(rule.getTags()))
                .bind(12, rule.getOnSuccess() != null ? rule.getOnSuccess().name() : null)
                .bind(13, rule.getOnFailure() != null ? rule.getOnFailure().name() : null)
                .bind(14, rule.isDisabled())
                .execute();
    }

    private ContractRuleSetDto buildRuleSet(List<Pair<String, ContractRuleDto>> rows) {
        List<ContractRuleDto> domainRules = new ArrayList<>();
        List<ContractRuleDto> migrationRules = new ArrayList<>();
        for (Pair<String, ContractRuleDto> row : rows) {
            if (CATEGORY_DOMAIN.equals(row.getLeft())) {
                domainRules.add(row.getRight());
            } else if (CATEGORY_MIGRATION.equals(row.getLeft())) {
                migrationRules.add(row.getRight());
            }
        }
        return ContractRuleSetDto.builder().domainRules(domainRules)
                .migrationRules(migrationRules).build();
    }

    private String serializeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RegistryStorageException("Failed to serialize contract rule params", e);
        }
    }

    private String serializeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RegistryStorageException("Failed to serialize contract rule tags", e);
        }
    }
}
