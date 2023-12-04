package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;


public class ArtifactRuleEntityMapper implements RowMapper<ArtifactRuleEntity> {

    public static final ArtifactRuleEntityMapper instance = new ArtifactRuleEntityMapper();

    /**
     * Constructor.
     */
    private ArtifactRuleEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactRuleEntity map(ResultSet rs) throws SQLException {
        ArtifactRuleEntity entity = new ArtifactRuleEntity();
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.artifactId = rs.getString("artifactId");
        entity.type = RuleType.fromValue(rs.getString("type"));
        entity.configuration = rs.getString("configuration");
        return entity;
    }

}