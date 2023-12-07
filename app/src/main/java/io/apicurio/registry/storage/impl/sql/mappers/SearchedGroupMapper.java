package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchedGroupMapper implements RowMapper<SearchedGroupDto> {

    public static final SearchedGroupMapper instance = new SearchedGroupMapper();

    /**
     * Constructor.
     */
    private SearchedGroupMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public SearchedGroupDto map(ResultSet rs) throws SQLException {
        SearchedGroupDto dto = new SearchedGroupDto();
        dto.setId(rs.getString("groupId"));
        dto.setCreatedBy(rs.getString("createdBy"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setDescription(rs.getString("description"));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        return dto;
    }
}
