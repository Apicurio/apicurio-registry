package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BranchEntityMapper implements RowMapper<BranchEntity> {

    public static final BranchEntityMapper instance = new BranchEntityMapper();

    private BranchEntityMapper() {
    }

    @Override
    public BranchEntity map(ResultSet rs) throws SQLException {
        return BranchEntity.builder()
                .groupId(RegistryContentUtils.denormalizeGroupId(rs.getString("groupId")))
                .artifactId(rs.getString("artifactId")).branchId(rs.getString("branchId"))
                .description(rs.getString("description")).systemDefined(rs.getBoolean("systemDefined"))
                .owner(rs.getString("owner")).createdOn(rs.getTimestamp("createdOn").getTime())
                .modifiedBy(rs.getString("modifiedBy")).modifiedOn(rs.getTimestamp("modifiedOn").getTime())
                .build();
    }
}
