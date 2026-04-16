package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.v3.ContractRuleEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ContractRuleEntityMapper implements RowMapper<ContractRuleEntity> {

    public static final ContractRuleEntityMapper instance = new ContractRuleEntityMapper();

    private ContractRuleEntityMapper() {
    }

    @Override
    public ContractRuleEntity map(ResultSet rs) throws SQLException {
        ContractRuleEntity entity = new ContractRuleEntity();
        entity.groupId = RegistryContentUtils.denormalizeGroupId(rs.getString("groupId"));
        entity.artifactId = rs.getString("artifactId");
        long globalId = rs.getLong("globalId");
        entity.globalId = rs.wasNull() ? null : globalId;
        entity.ruleCategory = rs.getString("ruleCategory");
        entity.orderIndex = rs.getInt("orderIndex");
        entity.ruleName = rs.getString("ruleName");
        entity.kind = rs.getString("kind");
        entity.ruleType = rs.getString("ruleType");
        entity.mode = rs.getString("mode");
        entity.expr = rs.getString("expr");
        entity.params = rs.getString("params");
        entity.tags = rs.getString("tags");
        entity.onSuccess = rs.getString("onSuccess");
        entity.onFailure = rs.getString("onFailure");
        entity.disabled = rs.getBoolean("disabled");
        return entity;
    }
}
