package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.model.GAV;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GAVMapper implements RowMapper<GAV> {

    public static final GAVMapper instance = new GAVMapper();


    private GAVMapper() {
    }


    @Override
    public GAV map(ResultSet rs) throws SQLException {
        return new GAV(
                rs.getString("groupId"),
                rs.getString("artifactId"),
                rs.getString("version")
        );
    }
}
