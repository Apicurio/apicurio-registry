package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BranchMetaDataDtoMapper implements RowMapper<BranchMetaDataDto> {

    public static final BranchMetaDataDtoMapper instance = new BranchMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private BranchMetaDataDtoMapper() {
    }

    /**
     * @see RowMapper#map(ResultSet)
     */
    @Override
    public BranchMetaDataDto map(ResultSet rs) throws SQLException {
        return BranchMetaDataDto.builder()
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