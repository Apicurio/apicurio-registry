package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mapper for search results that include a window function count (total_count).
 * Returns a Pair containing the SearchedGroupDto and the total count.
 */
public class SearchedGroupWithCountMapper implements RowMapper<Pair<SearchedGroupDto, Long>> {

    public static final SearchedGroupWithCountMapper instance = new SearchedGroupWithCountMapper();

    private SearchedGroupWithCountMapper() {
    }

    @Override
    public Pair<SearchedGroupDto, Long> map(ResultSet rs) throws SQLException {
        SearchedGroupDto dto = SearchedGroupMapper.instance.map(rs);
        long totalCount = rs.getLong("total_count");
        return Pair.of(dto, totalCount);
    }
}
