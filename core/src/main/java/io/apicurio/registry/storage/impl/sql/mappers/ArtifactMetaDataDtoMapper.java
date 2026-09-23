package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

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
        dto.setGroupId(RegistryContentUtils.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("labels")));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn").getTime());
        dto.setArtifactType(rs.getString("type"));
        return dto;
    }

}