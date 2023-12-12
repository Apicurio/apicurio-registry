package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.CommentEntity;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CommentEntityMapper implements RowMapper<CommentEntity> {

    public static final CommentEntityMapper instance = new CommentEntityMapper();

    /**
     * Constructor.
     */
    private CommentEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public CommentEntity map(ResultSet rs) throws SQLException {
        CommentEntity entity = new CommentEntity();
        entity.globalId = rs.getLong("globalId");
        entity.commentId = rs.getString("commentId");
        entity.createdBy = rs.getString("createdBy");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.value = rs.getString("cvalue");
        return entity;
    }

}