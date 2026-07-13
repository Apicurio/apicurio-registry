package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class SearchedVersionMapper implements RowMapper<SearchedVersionDto> {

    public static final SearchedVersionMapper instance = new SearchedVersionMapper();

    /**
     * Constructor.
     */
    private SearchedVersionMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public SearchedVersionDto map(ResultSet rs) throws SQLException {
        SearchedVersionDto dto = new SearchedVersionDto();
        dto.setGroupId(RegistryContentUtils.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionOrder(rs.getInt("versionOrder"));
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(VersionState.valueOf(rs.getString("state")));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setArtifactType(rs.getString("type"));
        dto.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("labels")));
        dto.setReferences(readReferences(rs));
        return dto;
    }

    private static List<ArtifactReferenceDto> readReferences(ResultSet rs) throws SQLException {
        try {
            return RegistryContentUtils.deserializeReferences(rs.getString("refs"));
        } catch (SQLException e) {
            // Query did not select content.refs (e.g. count-only paths should not hit this mapper).
            return Collections.emptyList();
        }
    }

}