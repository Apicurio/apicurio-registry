package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactState;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchedArtifactMapper implements RowMapper<SearchedArtifactDto> {

    public static final SearchedArtifactMapper instance = new SearchedArtifactMapper();

    /**
     * Constructor.
     */
    private SearchedArtifactMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public SearchedArtifactDto map(ResultSet rs) throws SQLException {
        SearchedArtifactDto dto = new SearchedArtifactDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setId(rs.getString("artifactId"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        // dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        dto.setType(rs.getString("type"));
        return dto;
    }

}