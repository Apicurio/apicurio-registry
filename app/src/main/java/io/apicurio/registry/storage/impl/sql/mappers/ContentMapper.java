package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ContentMapper implements RowMapper<ContentWrapperDto> {

    public static final ContentMapper instance = new ContentMapper();

    /**
     * Constructor.
     */
    private ContentMapper() {
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
        return contentWrapperDto;
    }

}