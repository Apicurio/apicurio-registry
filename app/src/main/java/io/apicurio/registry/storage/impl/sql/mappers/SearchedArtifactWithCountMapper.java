package io.apicurio.registry.storage.impl.sql.mappers;

import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchedArtifactWithCountMapper implements RowMapper<Pair<SearchedArtifactDto, Long>> {

    public static final SearchedArtifactWithCountMapper instance = new SearchedArtifactWithCountMapper();

    private SearchedArtifactWithCountMapper() {
    }

    @Override
    public Pair<SearchedArtifactDto, Long> map(ResultSet rs) throws SQLException {
        SearchedArtifactDto dto = new SearchedArtifactDto();
        dto.setGroupId(RegistryContentUtils.denormalizeGroupId(rs.getString("groupId")));
        dto.setArtifactId(rs.getString("artifactId"));
        dto.setOwner(rs.getString("owner"));
        dto.setCreatedOn(rs.getTimestamp("createdOn"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setModifiedBy(rs.getString("modifiedBy"));
        dto.setModifiedOn(rs.getTimestamp("modifiedOn"));
        dto.setArtifactType(rs.getString("type"));
        dto.setLabels(RegistryContentUtils.deserializeLabels(rs.getString("labels")));

        long totalCount = rs.getLong("total_count");
        return ImmutablePair.of(dto, totalCount);
    }

}
