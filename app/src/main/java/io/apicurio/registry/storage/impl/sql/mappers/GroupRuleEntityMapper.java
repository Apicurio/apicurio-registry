package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.GroupRuleEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GroupRuleEntityMapper implements RowMapper<GroupRuleEntity> {

    public static final GroupRuleEntityMapper instance = new GroupRuleEntityMapper();

    /**
     * Constructor.
     */
    private GroupRuleEntityMapper() {
    }

    /**
     * @see RowMapper#map(ResultSet)
     */
    @Override
    public GroupRuleEntity map(ResultSet rs) throws SQLException {
        GroupRuleEntity entity = new GroupRuleEntity();
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.type = RuleType.fromValue(rs.getString("type"));
        entity.configuration = rs.getString("configuration");
        return entity;
    }

}