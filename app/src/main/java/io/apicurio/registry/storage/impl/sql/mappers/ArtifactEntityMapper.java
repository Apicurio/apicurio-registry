package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.ArtifactEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactEntityMapper implements RowMapper<ArtifactEntity> {

    public static final ArtifactEntityMapper instance = new ArtifactEntityMapper();

    /**
     * Constructor.
     */
    private ArtifactEntityMapper() {
    }

    /**
     * @see RowMapper#map(ResultSet)
     */
    @Override
    public ArtifactEntity map(ResultSet rs) throws SQLException {
        ArtifactEntity entity = new ArtifactEntity();
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.artifactId = rs.getString("artifactId");
        entity.artifactType = rs.getString("type");
        entity.name = rs.getString("name");
        entity.description = rs.getString("description");
        entity.labels = SqlUtil.deserializeLabels(rs.getString("labels"));
        entity.owner = rs.getString("owner");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.modifiedBy = rs.getString("modifiedBy");
        entity.modifiedOn = rs.getTimestamp("modifiedOn").getTime();
        return entity;
    }

}