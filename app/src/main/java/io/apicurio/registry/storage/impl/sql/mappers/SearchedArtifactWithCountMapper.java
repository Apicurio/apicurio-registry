package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mapper for search results that include a window function count (total_count).
 * Returns a Pair containing the SearchedArtifactDto and the total count.
 */
public class SearchedArtifactWithCountMapper implements RowMapper<Pair<SearchedArtifactDto, Long>> {

    public static final SearchedArtifactWithCountMapper instance = new SearchedArtifactWithCountMapper();

    private SearchedArtifactWithCountMapper() {
    }

    @Override
    public Pair<SearchedArtifactDto, Long> map(ResultSet rs) throws SQLException {
        SearchedArtifactDto dto = SearchedArtifactMapper.instance.map(rs);
        long totalCount = rs.getLong("total_count");
        return Pair.of(dto, totalCount);
    }
}
