package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CommentDtoMapper implements RowMapper<CommentDto> {

    public static final CommentDtoMapper instance = new CommentDtoMapper();

    /**
     * Constructor.
     */
    private CommentDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public CommentDto map(ResultSet rs) throws SQLException {
        return CommentDto.builder().commentId(rs.getString("commentId")).owner(rs.getString("owner"))
                .createdOn(rs.getTimestamp("createdOn").getTime()).value(rs.getString("cvalue")).build();
    }

}