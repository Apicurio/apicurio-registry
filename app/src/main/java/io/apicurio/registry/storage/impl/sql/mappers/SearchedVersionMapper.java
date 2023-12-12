package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.ArtifactState;

import java.sql.ResultSet;
import java.sql.SQLException;

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
        dto.setGlobalId(rs.getLong("globalId"));
        dto.setVersion(rs.getString("version"));
        dto.setVersionId(rs.getInt("versionId"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));
        dto.setProperties(SqlUtil.deserializeProperties(rs.getString("properties")));
        dto.setType(rs.getString("type"));
        dto.setState(ArtifactState.valueOf(rs.getString("state")));
        return dto;
    }

}