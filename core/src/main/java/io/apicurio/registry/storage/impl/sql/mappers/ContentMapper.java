package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ContentMapper implements RowMapper<ContentWrapperDto> {

    public static final ContentMapper instance = new ContentMapper(false);

    /**
     * Variant that also reads an "artifactType" column from the result set, for queries that join content
     * with an artifact/version so the artifact type can be returned without a second round-trip.
     */
    public static final ContentMapper instanceWithArtifactType = new ContentMapper(true);

    private final boolean includeArtifactType;

    /**
     * Constructor.
     */
    private ContentMapper(boolean includeArtifactType) {
        this.includeArtifactType = includeArtifactType;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ContentWrapperDto map(ResultSet rs) throws SQLException {
        final ContentWrapperDto contentWrapperDto = new ContentWrapperDto();
        byte[] contentBytes = rs.getBytes("content");
        ContentHandle content = ContentHandle.create(contentBytes);
        contentWrapperDto.setContent(content);
        contentWrapperDto.setContentType(rs.getString("contentType"));
        contentWrapperDto.setReferences(RegistryContentUtils.deserializeReferences(rs.getString("refs")));
        contentWrapperDto.setContentHash(rs.getString("contentHash"));
        if (includeArtifactType) {
            contentWrapperDto.setArtifactType(rs.getString("artifactType"));
        }
        return contentWrapperDto;
    }

}