package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.BranchDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtifactVersionBranchDtoMapper implements RowMapper<BranchDto> {

    public static final ArtifactVersionBranchDtoMapper instance = new ArtifactVersionBranchDtoMapper();


    private ArtifactVersionBranchDtoMapper() {
    }


    @Override
    public BranchDto map(ResultSet rs) throws SQLException {
        return BranchDto.builder()
                .groupId(rs.getString("groupId"))
                .artifactId(rs.getString("artifactId"))
                .branch(rs.getString("branch"))
                .branchOrder(rs.getInt("branchOrder"))
                .version(rs.getString("version"))
                .build();
    }
}
