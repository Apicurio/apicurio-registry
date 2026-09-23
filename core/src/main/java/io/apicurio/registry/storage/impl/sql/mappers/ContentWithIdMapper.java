package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;

/**
 * Row mapper for batch content loading that captures both contentId and content data.
 * Returns a Map.Entry where key is contentId and value is ContentWrapperDto.
 */
public class ContentWithIdMapper implements RowMapper<Map.Entry<Long, ContentWrapperDto>> {

    public static final ContentWithIdMapper instance = new ContentWithIdMapper();

    private ContentWithIdMapper() {
    }

    @Override
    public Map.Entry<Long, ContentWrapperDto> map(ResultSet rs) throws SQLException {
        long contentId = rs.getLong("contentId");

        ContentWrapperDto contentWrapperDto = new ContentWrapperDto();
        byte[] contentBytes = rs.getBytes("content");
        ContentHandle content = ContentHandle.create(contentBytes);
        contentWrapperDto.setContent(content);
        contentWrapperDto.setContentType(rs.getString("contentType"));
        contentWrapperDto.setReferences(RegistryContentUtils.deserializeReferences(rs.getString("refs")));
        contentWrapperDto.setContentHash(rs.getString("contentHash"));

        return new AbstractMap.SimpleEntry<>(contentId, contentWrapperDto);
    }
}
