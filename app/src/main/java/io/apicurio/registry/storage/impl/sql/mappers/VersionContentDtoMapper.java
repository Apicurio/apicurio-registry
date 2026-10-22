package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.VersionContentDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a ResultSet row from a joined versions+artifacts+content query to a
 * {@link VersionContentDto} instance. Used by the startup reindexer's streaming query.
 */
public class VersionContentDtoMapper implements RowMapper<VersionContentDto> {

    public static final VersionContentDtoMapper instance = new VersionContentDtoMapper();

    private VersionContentDtoMapper() {
    }

    @Override
    public VersionContentDto map(ResultSet rs) throws SQLException {
        VersionContentDto dto = new VersionContentDto();
        dto.setGroupId(RegistryContentUtils.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionOrder(rs.getInt("versionOrder"));
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(VersionState.valueOf(rs.getString("state")));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn").getTime());
        dto.setArtifactType(rs.getString("type"));
        dto.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("labels")));
        dto.setContent(ContentHandle.create(rs.getBytes("content")));
        return dto;
    }
}
