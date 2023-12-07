package io.apicurio.registry.storage.impl.sql.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.ContentEntity;

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
        entity.canonicalHash = rs.getString("canonicalHash");
        entity.contentHash = rs.getString("contentHash");
        entity.contentBytes = rs.getBytes("content");
        try {
            entity.serializedReferences = rs.getString("artifactreferences");
        } catch (Exception e) {
            //The old database does not have te artifactreferences column, just ignore;
        }
        return entity;
    }

}