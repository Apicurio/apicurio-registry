package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactVersionEntityMapper implements RowMapper<ArtifactVersionEntity> {

    public static final ArtifactVersionEntityMapper instance = new ArtifactVersionEntityMapper();

    /**
     * Constructor.
     */
    private ArtifactVersionEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactVersionEntity map(ResultSet rs) throws SQLException {
        ArtifactVersionEntity entity = new ArtifactVersionEntity();
        entity.globalId = rs.getLong("globalId");
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.artifactId = rs.getString("artifactId");
        entity.version = rs.getString("version");
        entity.versionOrder = rs.getInt("versionOrder");
        entity.name = rs.getString("name");
        entity.description = rs.getString("description");
        entity.owner = rs.getString("owner");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.modifiedBy = rs.getString("modifiedBy");
        entity.modifiedOn = rs.getTimestamp("modifiedOn").getTime();
        entity.state = VersionState.valueOf(rs.getString("state"));
        entity.labels = SqlUtil.deserializeLabels(rs.getString("labels"));
        entity.contentId = rs.getLong("contentId");
        return entity;
    }

}