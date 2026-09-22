package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PeerDtoMapper implements RowMapper<PeerDto> {

    public static final PeerDtoMapper instance = new PeerDtoMapper();

    /**
     * Constructor.
     */
    private PeerDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public PeerDto map(ResultSet rs) throws SQLException {
        PeerDto dto = new PeerDto();
        dto.setPeerId(rs.getString("peerId"));
        dto.setUrl(rs.getString("url"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setEnabled(rs.getBoolean("enabled"));
        dto.setCredentialSecretRef(rs.getString("credentialSecretRef"));
        return dto;
    }

}
