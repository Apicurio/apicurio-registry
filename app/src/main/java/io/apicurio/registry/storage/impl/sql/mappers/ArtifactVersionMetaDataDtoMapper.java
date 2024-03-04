package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;

/**
 * Used to map a single row in the versions table to a {@link ArtifactVersionMetaDataDto} instance.
 */
public class ArtifactVersionMetaDataDtoMapper implements RowMapper<ArtifactVersionMetaDataDto> {

    public static final ArtifactVersionMetaDataDtoMapper instance = new ArtifactVersionMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private ArtifactVersionMetaDataDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactVersionMetaDataDto map(ResultSet rs) throws SQLException {
        ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(VersionState.valueOf(rs.getString("state")));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionOrder(rs.getInt("versionOrder"));
        dto.setType(rs.getString("type"));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        return dto;
    }

}