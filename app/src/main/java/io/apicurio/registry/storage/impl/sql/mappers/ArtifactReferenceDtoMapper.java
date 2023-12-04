package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;


public class ArtifactReferenceDtoMapper implements RowMapper<ArtifactReferenceDto> {

    public static final ArtifactReferenceDtoMapper instance = new ArtifactReferenceDtoMapper();

    /**
     * Constructor.
     */
    private ArtifactReferenceDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ArtifactReferenceDto map(ResultSet rs) throws SQLException {
        ArtifactReferenceDto dto = new ArtifactReferenceDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setVersion(rs.getString("version"));
        dto.setName(rs.getString("name"));
        return dto;
    }

}