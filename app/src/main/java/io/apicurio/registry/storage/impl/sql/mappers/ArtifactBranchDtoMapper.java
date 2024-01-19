package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ArtifactBranchDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactBranchDtoMapper implements RowMapper<ArtifactBranchDto> {

    public static final ArtifactBranchDtoMapper instance = new ArtifactBranchDtoMapper();


    private ArtifactBranchDtoMapper() {
    }


    @Override
    public ArtifactBranchDto map(ResultSet rs) throws SQLException {
        return ArtifactBranchDto.builder()
                .groupId(rs.getString("groupId"))
                .artifactId(rs.getString("artifactId"))
                .branchId(rs.getString("branchId"))
                .branchOrder(rs.getInt("branchOrder"))
                .version(rs.getString("version"))
                .build();
    }
}
