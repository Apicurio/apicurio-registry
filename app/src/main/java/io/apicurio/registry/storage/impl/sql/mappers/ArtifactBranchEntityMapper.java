package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.ArtifactBranchEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactBranchEntityMapper implements RowMapper<ArtifactBranchEntity> {

    public static final ArtifactBranchEntityMapper instance = new ArtifactBranchEntityMapper();


    private ArtifactBranchEntityMapper() {
    }


    @Override
    public ArtifactBranchEntity map(ResultSet rs) throws SQLException {
        return ArtifactBranchEntity.builder()
                .groupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")))
                .artifactId(rs.getString("artifactId"))
                .branchId(rs.getString("branchId"))
                .branchOrder(rs.getInt("branchOrder"))
                .version(rs.getString("version"))
                .build();
    }
}
