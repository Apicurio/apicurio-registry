package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.ContentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ContentEntityMapper implements RowMapper<ContentEntity> {

    public static final ContentEntityMapper instance = new ContentEntityMapper();

    /**
     * Constructor.
     */
    private ContentEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public ContentEntity map(ResultSet rs) throws SQLException {
        ContentEntity entity = new ContentEntity();
        entity.contentId = rs.getLong("contentId");
        entity.contentType = rs.getString("contentType");
        entity.canonicalHash = rs.getString("canonicalHash");
        entity.contentHash = rs.getString("contentHash");
        entity.contentBytes = rs.getBytes("content");
        try {
            entity.serializedReferences = rs.getString("refs");
        } catch (Exception e) {
            // The old database does not have te references column, just ignore;
        }
        return entity;
    }

}