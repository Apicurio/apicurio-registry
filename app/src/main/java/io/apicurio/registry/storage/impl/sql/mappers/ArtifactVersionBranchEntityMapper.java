package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.ArtifactVersionBranchEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactVersionBranchEntityMapper implements RowMapper<ArtifactVersionBranchEntity> {

    public static final ArtifactVersionBranchEntityMapper instance = new ArtifactVersionBranchEntityMapper();


    private ArtifactVersionBranchEntityMapper() {
    }


    @Override
    public ArtifactVersionBranchEntity map(ResultSet rs) throws SQLException {
        return ArtifactVersionBranchEntity.builder()
                .groupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")))
                .artifactId(rs.getString("artifactId"))
                .branch(rs.getString("branch"))
                .branchOrder(rs.getInt("branchOrder"))
                .version(rs.getString("version"))
                .build();
    }
}
