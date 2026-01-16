package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchedGroupWithCountMapper implements RowMapper<Pair<SearchedGroupDto, Long>> {

    public static final SearchedGroupWithCountMapper instance = new SearchedGroupWithCountMapper();

    private SearchedGroupWithCountMapper() {
    }

    @Override
    public Pair<SearchedGroupDto, Long> map(ResultSet rs) throws SQLException {
        SearchedGroupDto dto = new SearchedGroupDto();
        dto.setId(rs.getString("groupId"));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setDescription(rs.getString("description"));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        dto.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("labels")));

        long totalCount = rs.getLong("total_count");
        return ImmutablePair.of(dto, totalCount);
    }

}
