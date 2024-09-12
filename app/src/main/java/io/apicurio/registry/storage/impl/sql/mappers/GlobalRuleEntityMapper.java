package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GlobalRuleEntityMapper implements RowMapper<GlobalRuleEntity> {

    public static final GlobalRuleEntityMapper instance = new GlobalRuleEntityMapper();

    /**
     * Constructor.
     */
    private GlobalRuleEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public GlobalRuleEntity map(ResultSet rs) throws SQLException {
        GlobalRuleEntity entity = new GlobalRuleEntity();
        entity.ruleType = RuleType.fromValue(rs.getString("type"));
        entity.configuration = rs.getString("configuration");
        return entity;
    }

}