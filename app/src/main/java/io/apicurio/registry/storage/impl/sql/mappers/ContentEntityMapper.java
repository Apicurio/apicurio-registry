package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.ContentHashDto;
import io.apicurio.registry.storage.dto.ContentHashType;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * Maps a SQL result set row to a ContentEntity instance for export purposes.
 *
 * Note: This mapper only handles the basic content table fields. The content hashes
 * must be populated separately by querying the content_hashes table and calling
 * the populateHashes() method or by using the enrichContentEntityWithHashes() helper.
 */
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
        entity.contentHash = rs.getString("contentHash");
        entity.contentBytes = rs.getBytes("content");
        entity.serializedReferences = rs.getString("refs");

        // Initialize the hashes map (will be populated later from content_hashes table)
        entity.hashes = new HashMap<>();

        return entity;
    }

    /**
     * Populates the hash values for a ContentEntity from a ContentHashDto.
     * This is a convenience overload for use with ContentHashMapper.
     *
     * @param entity the content entity to populate
     * @param hashDto the hash DTO containing hash type and value
     */
    public static void addHash(ContentEntity entity, ContentHashDto hashDto) {
        String hashType = hashDto.getHashType().value();
        String hashValue = hashDto.getHashValue();

        if (entity.hashes == null) {
            entity.hashes = new HashMap<>();
        }
        entity.hashes.put(hashType, hashValue);

        // Also populate the deprecated fields for backward compatibility with old import code
        if (ContentHashType.CONTENT_SHA256.value().equals(hashType)) {
            entity.contentHash = hashValue;
        } else if (ContentHashType.CANONICAL_SHA256.value().equals(hashType)) {
            entity.canonicalHash = hashValue;
        }
    }

}