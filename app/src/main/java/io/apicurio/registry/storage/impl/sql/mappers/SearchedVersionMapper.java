package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.types.VersionState;

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
        dto.setVersionOrder(rs.getInt("versionOrder"));
        dto.setContentId(rs.getLong("contentId"));
        dto.setState(VersionState.valueOf(rs.getString("state")));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setType(rs.getString("type"));
        return dto;
    }

}