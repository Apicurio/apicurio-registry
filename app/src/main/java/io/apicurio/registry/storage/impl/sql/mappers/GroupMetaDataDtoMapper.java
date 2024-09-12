package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class GroupMetaDataDtoMapper implements RowMapper<GroupMetaDataDto> {

    public static final GroupMetaDataDtoMapper instance = new GroupMetaDataDtoMapper();

    /**
     * Constructor.
     */
    private GroupMetaDataDtoMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public GroupMetaDataDto map(ResultSet rs) throws SQLException {
        GroupMetaDataDto dto = new GroupMetaDataDto();
        dto.setGroupId(SqlUtil.denormalizeGroupId(rs.getString("groupId")));
        dto.setDescription(rs.getString("description"));

        String type = rs.getString("artifactsType");
        dto.setArtifactsType(type);

        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn").getTime());

        dto.setModifiedBy(rs.getString("modifiedBy"));
        Timestamp modifiedOn = rs.getTimestamp("modifiedOn");
        dto.setModifiedOn(modifiedOn == null ? 0 : modifiedOn.getTime());

        dto.setLabels(SqlUtil.deserializeLabels(rs.getString("labels")));

        return dto;
    }

}