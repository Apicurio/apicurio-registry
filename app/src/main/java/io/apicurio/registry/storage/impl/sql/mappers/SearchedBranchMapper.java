package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedBranchDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchedBranchMapper implements RowMapper<SearchedBranchDto> {

    public static final SearchedBranchMapper instance = new SearchedBranchMapper();

    /**
     * Constructor.
     */
    private SearchedBranchMapper() {
    }

    /**
     * @see RowMapper#map(ResultSet)
     */
    @Override
    public SearchedBranchDto map(ResultSet rs) throws SQLException {
        return SearchedBranchDto.builder()
                .groupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")))
                .artifactId(rs.getString("artifactId"))
                .branchId(rs.getString("branchId"))
                .description(rs.getString("description"))
                .systemDefined(rs.getBoolean("systemDefined"))
                .owner(rs.getString("owner"))
                .createdOn(rs.getTimestamp("createdOn").getTime())
                .modifiedBy(rs.getString("modifiedBy"))
                .modifiedOn(rs.getTimestamp("modifiedOn").getTime())
                .build();
    }
}
