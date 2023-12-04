package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;


public class StoredArtifactMapper implements RowMapper<StoredArtifactDto> {

    public static final StoredArtifactMapper instance = new StoredArtifactMapper();

    /**
     * Constructor.
     */
    private StoredArtifactMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public StoredArtifactDto map(ResultSet rs) throws SQLException {
        long globalId = rs.getLong("globalId");
        String version = rs.getString("version");
        int versionId = rs.getInt("versionId");
        Long contentId = rs.getLong("contentId");
        byte[] contentBytes = rs.getBytes("content");
        ContentHandle content = ContentHandle.create(contentBytes);
        List<ArtifactReferenceDto> references = SqlUtil.deserializeReferences(rs.getString("artifactreferences"));
        return StoredArtifactDto.builder().content(content).contentId(contentId).globalId(globalId).version(version).versionId(versionId).references(references).build();
    }

}