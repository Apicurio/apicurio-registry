package io.apicurio.registry.storage.impl.sql.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ContractRuleDtoMapper implements RowMapper<ContractRuleDto> {

    public static final ContractRuleDtoMapper instance = new ContractRuleDtoMapper();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ContractRuleDtoMapper() {
    }

    @Override
    public ContractRuleDto map(ResultSet rs) throws SQLException {
        ContractRuleDto dto = new ContractRuleDto();
        dto.setName(rs.getString("ruleName"));
        dto.setKind(RuleKind.valueOf(rs.getString("kind")));
        dto.setType(rs.getString("ruleType"));
        dto.setMode(RuleMode.valueOf(rs.getString("mode")));
        dto.setExpr(rs.getString("expr"));
        dto.setParams(deserializeParams(rs.getString("params")));
        dto.setTags(deserializeTags(rs.getString("tags")));
        String onSuccess = rs.getString("onSuccess");
        if (onSuccess != null) {
            dto.setOnSuccess(RuleAction.valueOf(onSuccess));
        }
        String onFailure = rs.getString("onFailure");
        if (onFailure != null) {
            dto.setOnFailure(RuleAction.valueOf(onFailure));
        }
        dto.setDisabled(rs.getBoolean("disabled"));
        dto.setOrderIndex(rs.getInt("orderIndex"));
        return dto;
    }

    private Map<String, String> deserializeParams(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RegistryStorageException("Failed to deserialize contract rule params", e);
        }
    }

    private Set<String> deserializeTags(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RegistryStorageException("Failed to deserialize contract rule tags", e);
        }
    }
}
