package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.utils.impexp.GroupEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static java.util.Optional.ofNullable;


public class GroupEntityMapper implements RowMapper<GroupEntity> {

    public static final GroupEntityMapper instance = new GroupEntityMapper();

    /**
     * Constructor.
     */
    private GroupEntityMapper() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.jdb.RowMapper#map(java.sql.ResultSet)
     */
    @Override
    public GroupEntity map(ResultSet rs) throws SQLException {
        GroupEntity entity = new GroupEntity();
        entity.groupId = SqlUtil.denormalizeGroupId(rs.getString("groupId"));
        entity.description = rs.getString("description");
        String type = rs.getString("artifactsType");
        entity.artifactsType = type;
        entity.createdBy = rs.getString("createdBy");
        entity.createdOn = rs.getTimestamp("createdOn").getTime();
        entity.modifiedBy = rs.getString("modifiedBy");
        entity.modifiedOn = ofNullable(rs.getTimestamp("modifiedOn")).map(Timestamp::getTime).orElse(0L);
        entity.properties = SqlUtil.deserializeProperties(rs.getString("properties"));
        return entity;
    }

}