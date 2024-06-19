package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StoredArtifactMapper implements RowMapper<StoredArtifactVersionDto> {

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
    public StoredArtifactVersionDto map(ResultSet rs) throws SQLException {
        return StoredArtifactVersionDto.builder().content(ContentHandle.create(rs.getBytes("content")))
                .contentType(rs.getString("contentType")).contentId(rs.getLong("contentId"))
                .globalId(rs.getLong("globalId")).version(rs.getString("version"))
                .versionOrder(rs.getInt("versionOrder"))
                .references(SqlUtil.deserializeReferences(rs.getString("refs"))).build();
    }
}
