package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactState;


public class ArtifactMetaDataDtoMapper implements RowMapper<ArtifactMetaDataDto> {

    public static final ArtifactMetaDataDtoMapper instance = new ArtifactMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private ArtifactMetaDataDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactMetaDataDto map(ResultSet rs) throws SQLException {
        ArtifactMetaDataDto dto = new ArtifactMetaDataDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setId(rs.getString("artifactId"));
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionId(rs.getInt("versionId"));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn").getTime());
        dto.setType(rs.getString("type"));
        return dto;
    }

}