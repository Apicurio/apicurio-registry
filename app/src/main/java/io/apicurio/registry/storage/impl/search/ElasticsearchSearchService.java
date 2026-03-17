package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that bridges the existing SearchFilter/OrderBy abstraction with Elasticsearch queries.
 * Translates search filters into ES queries, executes searches against the index, and maps
 * results back to the standard DTO types used by the REST API.
 */
@ApplicationScoped
public class ElasticsearchSearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchService.class);

    private static final Set<SearchFilterType> UNSUPPORTED_FILTER_TYPES = EnumSet.of(
            SearchFilterType.contentHash, SearchFilterType.canonicalHash);

    /**
     * Filter types that can only be handled by the search index and have no SQL fallback.
     */
    private static final Set<SearchFilterType> INDEX_ONLY_FILTER_TYPES = EnumSet.of(
            SearchFilterType.content, SearchFilterType.structure);

    @Inject
    ElasticsearchClient client;

    @Inject
    ElasticsearchSearchConfig config;

    @Inject
    ElasticsearchDocumentBuilder documentBuilder;

    /**
     * Checks whether all filters in the given set can be handled by Elasticsearch.
     *
     * @param filters the set of search filters to check
     * @return true if all filters can be handled by ES, false otherwise
     */
    public boolean canHandleFilters(Set<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (SearchFilter filter : filters) {
            if (UNSUPPORTED_FILTER_TYPES.contains(filter.getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the given filters include any that can only be served by the search index.
     *
     * @param filters the set of search filters to check
     * @return true if any filter requires the search index, false otherwise
     */
    public boolean requiresSearchIndex(Set<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }
        for (SearchFilter filter : filters) {
            if (INDEX_ONLY_FILTER_TYPES.contains(filter.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches the Elasticsearch index for versions matching the given filters, with sorting
     * and pagination.
     *
     * @param filters the search filters to apply
     * @param orderBy the field to sort by
     * @param orderDirection the sort direction (asc/desc)
     * @param offset the number of results to skip
     * @param limit the maximum number of results to return
     * @return a VersionSearchResultsDto containing matched versions and total count
     * @throws IOException if an error occurs during search
     */
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws IOException {

        Query query = buildEsQuery(filters);
        List<SortOptions> sortOptions = buildSort(orderBy, orderDirection);

        SearchResponse<Map> response = client.search(s -> {
            s.index(config.getIndexName())
                    .query(query)
                    .from(offset)
                    .size(limit)
                    .trackTotalHits(t -> t.enabled(true));

            for (SortOptions sortOption : sortOptions) {
                s.sort(sortOption);
            }

            return s;
        }, Map.class);

        long totalCount = response.hits().total() != null
                ? response.hits().total().value() : 0;

        List<SearchedVersionDto> versions = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                versions.add(mapToSearchedVersionDto(hit.source()));
            }
        }

        log.debug("Elasticsearch search returned {} results (total: {}, offset: {}, limit: {})",
                versions.size(), totalCount, offset, limit);

        return VersionSearchResultsDto.builder()
                .versions(versions)
                .count((int) totalCount)
                .build();
    }

    /**
     * Builds an Elasticsearch Query from a set of SearchFilters.
     *
     * @param filters the search filters to translate
     * @return an Elasticsearch Query
     */
    Query buildEsQuery(Set<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        BoolQuery.Builder builder = new BoolQuery.Builder();
        boolean hasPositiveClause = false;

        for (SearchFilter filter : filters) {
            Query filterQuery = buildFilterQuery(filter);
            if (filterQuery == null) {
                continue;
            }

            if (filter.isNot()) {
                builder.mustNot(filterQuery);
            } else {
                builder.must(filterQuery);
                hasPositiveClause = true;
            }
        }

        if (!hasPositiveClause) {
            builder.must(Query.of(q -> q.matchAll(m -> m)));
        }

        return Query.of(q -> q.bool(builder.build()));
    }

    /**
     * Translates a single SearchFilter into an Elasticsearch Query.
     *
     * @param filter the search filter
     * @return an Elasticsearch Query, or null if the filter type is unsupported
     */
    private Query buildFilterQuery(SearchFilter filter) {
        switch (filter.getType()) {
        case groupId:
            String groupValue = filter.getStringValue() == null ? "default"
                    : filter.getStringValue();
            return Query.of(q -> q.term(t -> t.field("groupId").value(groupValue)));

        case artifactId:
            return Query.of(q -> q.term(t -> t
                    .field("artifactId").value(filter.getStringValue())));

        case version:
            return Query.of(q -> q.term(t -> t
                    .field("version").value(filter.getStringValue())));

        case artifactType:
            return Query.of(q -> q.term(t -> t
                    .field("artifactType").value(filter.getStringValue())));

        case state:
            return Query.of(q -> q.term(t -> t
                    .field("state").value(filter.getStringValue().toUpperCase())));

        case globalId:
            return Query.of(q -> q.term(t -> t
                    .field("globalId").value(filter.getNumberValue().longValue())));

        case contentId:
            return Query.of(q -> q.term(t -> t
                    .field("contentId").value(filter.getNumberValue().longValue())));

        case name:
            return buildNameQuery(filter.getStringValue());

        case description:
            return buildTextQuery("description", filter.getStringValue());

        case content:
            return buildTextQuery("content", filter.getStringValue());

        case structure:
            return buildStructureQuery(filter.getStringValue());

        case labels:
            return buildLabelQuery(filter);

        case contentHash:
        case canonicalHash:
            log.warn("Unsupported Elasticsearch filter type: {}", filter.getType());
            return null;

        default:
            log.warn("Unknown filter type: {}", filter.getType());
            return null;
        }
    }

    /**
     * Builds a query for the name filter. Searches both "name" (full-text) and "artifactId"
     * (exact match) to match SQL behavior.
     *
     * @param value the search value
     * @return a query that matches name or artifactId
     */
    private Query buildNameQuery(String value) {
        return Query.of(q -> q.bool(b -> b
                .should(Query.of(sq -> sq.match(m -> m
                        .field("name").query(value).operator(Operator.And))))
                .should(Query.of(sq -> sq.term(t -> t
                        .field("artifactId").value(value))))
        ));
    }

    /**
     * Builds a full-text match query for a text field.
     *
     * @param field the field name
     * @param value the search text
     * @return a match query
     */
    private Query buildTextQuery(String field, String value) {
        return Query.of(q -> q.match(m -> m
                .field(field).query(value).operator(Operator.And)));
    }

    /**
     * Builds a query for the structure field using the faceted format. Supports three formats:
     * <ul>
     * <li>{@code type:kind:name} - exact match on the structure field</li>
     * <li>{@code kind:name} - text search on structure_text</li>
     * <li>{@code name} - text search on structure_text</li>
     * </ul>
     *
     * @param value the structure filter value
     * @return an Elasticsearch query for structured element search
     */
    private Query buildStructureQuery(String value) {
        if (value == null || value.isBlank()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        String lowered = value.toLowerCase().trim();
        String[] parts = lowered.split(":", -1);

        if (parts.length == 3) {
            // Full format: type:kind:name - exact match on structure field
            return Query.of(q -> q.term(t -> t
                    .field("structure").value(lowered)));
        } else if (parts.length == 2) {
            // Partial format: kind:name - text search on structure_text
            String textQuery = parts[0] + " " + parts[1];
            return buildTextQuery("structure_text", textQuery);
        } else {
            // Plain name: text search on structure_text field
            return buildTextQuery("structure_text", lowered);
        }
    }

    /**
     * Builds a label filter query using nested queries for key-value pairs.
     *
     * @param filter the label search filter
     * @return a nested query matching the label criteria
     */
    private Query buildLabelQuery(SearchFilter filter) {
        Pair<String, String> labelPair = filter.getLabelFilterValue();
        String key = labelPair.getLeft();
        String labelValue = labelPair.getRight();

        if (labelValue == null || labelValue.isBlank()) {
            // Key-only filter
            return Query.of(q -> q.nested(n -> n
                    .path("labels")
                    .scoreMode(ChildScoreMode.None)
                    .query(Query.of(nq -> nq.match(m -> m
                            .field("labels.key").query(key))))));
        }

        // Key + value filter
        return Query.of(q -> q.nested(n -> n
                .path("labels")
                .scoreMode(ChildScoreMode.None)
                .query(Query.of(nq -> nq.bool(b -> b
                        .must(Query.of(mq -> mq.match(m -> m
                                .field("labels.key").query(key))))
                        .must(Query.of(mq -> mq.match(m -> m
                                .field("labels.value").query(labelValue)))))))));
    }

    /**
     * Builds Elasticsearch sort options from OrderBy and OrderDirection.
     *
     * @param orderBy the field to sort by
     * @param orderDirection the sort direction
     * @return a list of sort options
     */
    List<SortOptions> buildSort(OrderBy orderBy, OrderDirection orderDirection) {
        if (orderBy == null) {
            return List.of(SortOptions.of(s -> s.score(sc -> sc)));
        }

        SortOrder sortOrder = orderDirection == OrderDirection.desc
                ? SortOrder.Desc : SortOrder.Asc;

        String fieldName;
        switch (orderBy) {
        case name:
            fieldName = "name.keyword";
            break;
        case createdOn:
            fieldName = "createdOn";
            break;
        case modifiedOn:
            fieldName = "modifiedOn";
            break;
        case globalId:
            fieldName = "globalId";
            break;
        case version:
            fieldName = "version";
            break;
        case groupId:
            fieldName = "groupId";
            break;
        case artifactId:
            fieldName = "artifactId";
            break;
        case artifactType:
            fieldName = "artifactType";
            break;
        default:
            return List.of(SortOptions.of(s -> s.score(sc -> sc)));
        }

        final String sortField = fieldName;
        return List.of(SortOptions.of(s -> s.field(FieldSort.of(f -> f
                .field(sortField).order(sortOrder)))));
    }

    /**
     * Maps an Elasticsearch source document to a SearchedVersionDto.
     *
     * @param source the document source map
     * @return a populated SearchedVersionDto
     */
    @SuppressWarnings("unchecked")
    SearchedVersionDto mapToSearchedVersionDto(Map<String, Object> source) {
        SearchedVersionDto.SearchedVersionDtoBuilder builder = SearchedVersionDto.builder();

        // ID fields
        Object globalId = source.get("globalId");
        if (globalId != null) {
            builder.globalId(toLong(globalId));
        }

        Object contentId = source.get("contentId");
        if (contentId != null) {
            builder.contentId(toLong(contentId));
        }

        // String fields
        builder.groupId(toStr(source.get("groupId")));
        builder.artifactId(toStr(source.get("artifactId")));
        builder.version(toStr(source.get("version")));
        builder.artifactType(toStr(source.get("artifactType")));
        builder.name(toStr(source.get("name")));
        builder.description(toStr(source.get("description")));
        builder.owner(toStr(source.get("owner")));
        builder.modifiedBy(toStr(source.get("modifiedBy")));

        // State
        Object state = source.get("state");
        if (state != null) {
            builder.state(io.apicurio.registry.types.VersionState.valueOf(toStr(state)));
        }

        // Timestamps
        Object createdOn = source.get("createdOn");
        if (createdOn != null) {
            builder.createdOn(new Date(toLong(createdOn)));
        }

        Object modifiedOn = source.get("modifiedOn");
        if (modifiedOn != null) {
            builder.modifiedOn(new Date(toLong(modifiedOn)));
        }

        // VersionOrder
        Object versionOrder = source.get("versionOrder");
        if (versionOrder != null) {
            builder.versionOrder(toInt(versionOrder));
        }

        // Labels
        Map<String, String> labels = documentBuilder.extractLabels(source);
        builder.labels(labels);

        return builder.build();
    }

    /**
     * Converts an Object to a long value, handling both Integer and Long types from JSON
     * deserialization.
     */
    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * Converts an Object to an int value.
     */
    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * Converts an Object to a String, returning null for null values.
     */
    private String toStr(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
