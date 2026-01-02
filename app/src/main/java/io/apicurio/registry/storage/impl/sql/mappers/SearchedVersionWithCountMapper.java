package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mapper for search results that include a window function count (total_count).
 * Returns a Pair containing the SearchedVersionDto and the total count.
 */
public class SearchedVersionWithCountMapper implements RowMapper<Pair<SearchedVersionDto, Long>> {

    public static final SearchedVersionWithCountMapper instance = new SearchedVersionWithCountMapper();

    private SearchedVersionWithCountMapper() {
    }

    @Override
    public Pair<SearchedVersionDto, Long> map(ResultSet rs) throws SQLException {
        SearchedVersionDto dto = SearchedVersionMapper.instance.map(rs);
        long totalCount = rs.getLong("total_count");
        return Pair.of(dto, totalCount);
    }
}
