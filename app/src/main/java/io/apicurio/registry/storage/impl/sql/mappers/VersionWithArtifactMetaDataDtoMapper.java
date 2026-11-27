package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.VersionWithArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a row of {@code selectArtifactVersionWithArtifactMetaDataByGlobalId}: a versions row, plus the
 * columns of its artifacts row under an {@code artifact} prefix.
 */
public class VersionWithArtifactMetaDataDtoMapper implements RowMapper<VersionWithArtifactMetaDataDto> {

    public static final VersionWithArtifactMetaDataDtoMapper instance = new VersionWithArtifactMetaDataDtoMapper();

    private VersionWithArtifactMetaDataDtoMapper() {
    }

    @Override
    public VersionWithArtifactMetaDataDto map(ResultSet rs) throws SQLException {
        ArtifactMetaDataDto artifact = new ArtifactMetaDataDto();
        artifact.setGroupId(RegistryContentUtils.denormalizeGroupId(rs.getString("artifactGroupId")));
        artifact.setArtifactId(rs.getString("artifactArtifactId"));
        artifact.setOwner(rs.getString("artifactOwner"));
        artifact.setCreatedOn(rs.getTimestamp("artifactCreatedOn").getTime());
        artifact.setName(rs.getString("artifactName"));
        artifact.setDescription(rs.getString("artifactDescription"));
        artifact.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("artifactLabels")));
        artifact.setModifiedBy(rs.getString("artifactModifiedBy"));
        artifact.setModifiedOn(rs.getTimestamp("artifactModifiedOn").getTime());
        artifact.setArtifactType(rs.getString("type"));
        return new VersionWithArtifactMetaDataDto(ArtifactVersionMetaDataDtoMapper.instance.map(rs), artifact);
    }
}
