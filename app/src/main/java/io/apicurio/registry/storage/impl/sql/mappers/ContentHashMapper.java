package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ContentHashDto;
import io.apicurio.registry.storage.dto.ContentHashType;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a SQL result set row to a ContentHashDto instance.
 */
public class ContentHashMapper implements RowMapper<ContentHashDto> {

    public static final ContentHashMapper instance = new ContentHashMapper();

    /**
     * Constructor.
     */
    private ContentHashMapper() {
    }

    /**
     * Maps a result set row to a ContentHashDto.
     *
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ContentHashDto map(ResultSet rs) throws SQLException {
        ContentHashDto dto = new ContentHashDto();
        dto.setContentId(rs.getLong("contentId"));
        dto.setHashType(ContentHashType.fromValue(rs.getString("hashType")));
        dto.setHashValue(rs.getString("hashValue"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());
        return dto;
    }
}
