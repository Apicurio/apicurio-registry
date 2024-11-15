package io.apicurio.common.apps.storage.sql.jdbi.mappers;

import io.apicurio.common.apps.content.handle.ContentHandle;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class ContentHandleMapper implements RowMapper<ContentHandle> {

    @Override
    public boolean supports(Class<?> klass) {
        return ContentHandle.class.equals(klass);
    }

    @Override
    public ContentHandle map(ResultSet rs) throws SQLException {
        return ContentHandle.create(rs.getBytes(1));
    }
}
