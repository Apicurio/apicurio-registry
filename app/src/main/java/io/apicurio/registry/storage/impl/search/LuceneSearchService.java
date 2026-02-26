package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
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
 * Service that bridges the existing SearchFilter/OrderBy abstraction with Lucene queries. Translates
 * search filters into Lucene queries, executes searches against the index, and maps results back to
 * the standard DTO types used by the REST API.
 */
@ApplicationScoped
public class LuceneSearchService {

    private static final Logger log = LoggerFactory.getLogger(LuceneSearchService.class);

    private static final Set<SearchFilterType> UNSUPPORTED_FILTER_TYPES = EnumSet.of(
            SearchFilterType.contentHash, SearchFilterType.canonicalHash);

    /**
     * Filter types that can only be handled by Lucene and have no SQL fallback. If a search
     * includes any of these filters and Lucene is unavailable, an error should be returned
     * rather than silently falling back to SQL (which would return incorrect results).
     */
    private static final Set<SearchFilterType> LUCENE_ONLY_FILTER_TYPES = EnumSet.of(
            SearchFilterType.content);

    @Inject
    LuceneIndexSearcher indexSearcher;

    @Inject
    LuceneDocumentBuilder documentBuilder;

    /**
     * Checks whether all filters in the given set can be handled by the Lucene index. Returns false
     * for filter types that are not present in the index (e.g. contentHash, canonicalHash).
     *
     * @param filters The set of search filters to check
     * @return true if all filters can be handled by Lucene, false otherwise
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
     * Checks whether the given filters include any that can only be served by Lucene. If this
     * returns true and Lucene is unavailable, the caller should return an error rather than
     * falling back to SQL (which would produce incorrect results).
     *
     * @param filters The set of search filters to check
     * @return true if any filter requires Lucene, false otherwise
     */
    public boolean requiresLucene(Set<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return false;
        }
        for (SearchFilter filter : filters) {
            if (LUCENE_ONLY_FILTER_TYPES.contains(filter.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches the Lucene index for versions matching the given filters, with sorting and
     * pagination.
     *
     * @param filters The search filters to apply
     * @param orderBy The field to sort by
     * @param orderDirection The sort direction (asc/desc)
     * @param offset The number of results to skip
     * @param limit The maximum number of results to return
     * @return A VersionSearchResultsDto containing matched versions and total count
     * @throws IOException if an error occurs during search
     */
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws IOException {
        Query query = buildLuceneQuery(filters);
        Sort sort = buildSort(orderBy, orderDirection);

        // Get total count
        int totalCount = indexSearcher.count(query);

        // Execute search with offset + limit to handle pagination
        int maxResults = offset + limit;
        if (maxResults <= 0) {
            maxResults = 1; // Lucene requires at least 1
        }

        List<SearchedVersionDto> versions = new ArrayList<>();

        if (totalCount > 0 && offset < totalCount) {
            TopDocs topDocs = indexSearcher.search(query, maxResults, sort);

            // Skip offset results and map remaining up to limit
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;
            int end = Math.min(scoreDocs.length, offset + limit);
            for (int i = offset; i < end; i++) {
                Document doc = indexSearcher.doc(scoreDocs[i].doc);
                versions.add(mapToSearchedVersionDto(doc));
            }
        }

        log.debug("Lucene search returned {} results (total: {}, offset: {}, limit: {})",
                versions.size(), totalCount, offset, limit);

        return VersionSearchResultsDto.builder()
                .versions(versions)
                .count(totalCount)
                .build();
    }

    /**
     * Builds a Lucene Query from a set of SearchFilters. Each filter is translated into the
     * appropriate Lucene query type and combined with BooleanQuery using MUST clauses (AND
     * semantics), or MUST_NOT for negated filters.
     *
     * @param filters The search filters to translate
     * @return A Lucene Query
     */
    Query buildLuceneQuery(Set<SearchFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return new MatchAllDocsQuery();
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasPositiveClause = false;

        for (SearchFilter filter : filters) {
            Query filterQuery = buildFilterQuery(filter);
            if (filterQuery == null) {
                continue;
            }

            if (filter.isNot()) {
                builder.add(filterQuery, BooleanClause.Occur.MUST_NOT);
            } else {
                builder.add(filterQuery, BooleanClause.Occur.MUST);
                hasPositiveClause = true;
            }
        }

        // If we only have MUST_NOT clauses, we need a MatchAllDocsQuery base
        if (!hasPositiveClause) {
            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        }

        return builder.build();
    }

    /**
     * Translates a single SearchFilter into a Lucene Query based on its type.
     *
     * @param filter The search filter
     * @return A Lucene Query, or null if the filter type is unsupported
     */
    private Query buildFilterQuery(SearchFilter filter) {
        switch (filter.getType()) {
        case groupId:
            return new TermQuery(new Term("groupId", filter.getStringValue() == null ? "default" : filter.getStringValue()));

        case artifactId:
            return new TermQuery(new Term("artifactId", filter.getStringValue()));

        case version:
            return new TermQuery(new Term("version", filter.getStringValue()));

        case artifactType:
            return new TermQuery(new Term("artifactType", filter.getStringValue()));

        case state:
            return new TermQuery(new Term("state", filter.getStringValue().toUpperCase()));

        case globalId:
            return LongPoint.newExactQuery("globalId_query", filter.getNumberValue().longValue());

        case contentId:
            return LongPoint.newExactQuery("contentId_query", filter.getNumberValue().longValue());

        case name:
            return buildNameQuery(filter.getStringValue());

        case description:
            return buildTextQuery("description", filter.getStringValue());

        case content:
            return buildTextQuery("content", filter.getStringValue());

        case labels:
            return buildLabelQuery(filter);

        case contentHash:
        case canonicalHash:
            // These are not in the Lucene index
            log.warn("Unsupported Lucene filter type: {}", filter.getType());
            return null;

        default:
            log.warn("Unknown filter type: {}", filter.getType());
            return null;
        }
    }

    /**
     * Builds a query for the name filter. Searches both the "name" field (full-text) and the
     * "artifactId" field (exact match) to match the SQL behavior where name search also checks
     * artifactId.
     *
     * @param value The search value
     * @return A query that matches name or artifactId
     */
    private Query buildNameQuery(String value) {
        BooleanQuery.Builder nameBuilder = new BooleanQuery.Builder();

        // Full-text search on name field
        Query nameQuery = buildTextQuery("name", value);
        if (nameQuery != null) {
            nameBuilder.add(nameQuery, BooleanClause.Occur.SHOULD);
        }

        // Also check artifactId for exact match (matching SQL behavior)
        nameBuilder.add(new TermQuery(new Term("artifactId", value)),
                BooleanClause.Occur.SHOULD);

        return nameBuilder.build();
    }

    /**
     * Builds a full-text query for a TextField using the analyzer from the index.
     *
     * @param field The field name
     * @param value The search text
     * @return A parsed query, or a TermQuery as fallback if parsing fails
     */
    private Query buildTextQuery(String field, String value) {
        try {
            QueryParser parser = new QueryParser(field, indexSearcher.getAnalyzer());
            parser.setDefaultOperator(QueryParser.Operator.AND);
            return parser.parse(QueryParser.escape(value));
        } catch (ParseException e) {
            log.warn("Failed to parse query for field '{}': {}", field, e.getMessage());
            // Fallback to simple term query with lowercased value
            return new TermQuery(new Term(field, value.toLowerCase()));
        }
    }

    /**
     * Builds a label filter query. Supports key-only and key+value filtering with
     * case-insensitive matching.
     *
     * @param filter The label search filter
     * @return A query matching the label criteria
     */
    private Query buildLabelQuery(SearchFilter filter) {
        Pair<String, String> labelPair = filter.getLabelFilterValue();
        String key = labelPair.getLeft();
        String value = labelPair.getRight();

        if (value == null || value.isBlank()) {
            // Key-only filter
            return buildTextQuery("label_key", key);
        }

        // Key + value filter
        BooleanQuery.Builder labelBuilder = new BooleanQuery.Builder();
        labelBuilder.add(buildTextQuery("label_key", key), BooleanClause.Occur.MUST);
        labelBuilder.add(buildTextQuery("label_value", value), BooleanClause.Occur.MUST);
        return labelBuilder.build();
    }

    /**
     * Builds a Lucene Sort from the OrderBy and OrderDirection parameters.
     *
     * @param orderBy The field to sort by
     * @param orderDirection The sort direction
     * @return A Lucene Sort, or Sort.RELEVANCE if orderBy is null
     */
    Sort buildSort(OrderBy orderBy, OrderDirection orderDirection) {
        if (orderBy == null) {
            return Sort.RELEVANCE;
        }

        boolean reverse = orderDirection == OrderDirection.desc;
        SortField sortField;

        switch (orderBy) {
        case name:
            sortField = new SortField("name_sort", SortField.Type.STRING, reverse);
            break;
        case createdOn:
            sortField = new SortField("createdOn_sort", SortField.Type.LONG, reverse);
            break;
        case modifiedOn:
            sortField = new SortField("modifiedOn_sort", SortField.Type.LONG, reverse);
            break;
        case globalId:
            sortField = new SortField("globalId_sort", SortField.Type.LONG, reverse);
            break;
        case version:
            sortField = new SortField("version_sort", SortField.Type.STRING, reverse);
            break;
        case groupId:
            sortField = new SortField("groupId_sort", SortField.Type.STRING, reverse);
            break;
        case artifactId:
            sortField = new SortField("artifactId_sort", SortField.Type.STRING, reverse);
            break;
        case artifactType:
            sortField = new SortField("artifactType_sort", SortField.Type.STRING, reverse);
            break;
        default:
            return Sort.RELEVANCE;
        }

        return new Sort(sortField);
    }

    /**
     * Maps a Lucene Document to a SearchedVersionDto by reading stored fields.
     *
     * @param doc The Lucene document
     * @return A populated SearchedVersionDto
     */
    SearchedVersionDto mapToSearchedVersionDto(Document doc) {
        SearchedVersionDto.SearchedVersionDtoBuilder builder = SearchedVersionDto.builder();

        // ID fields (stored as strings)
        String globalId = doc.get("globalId");
        if (globalId != null) {
            builder.globalId(Long.parseLong(globalId));
        }

        String contentId = doc.get("contentId");
        if (contentId != null) {
            builder.contentId(Long.parseLong(contentId));
        }

        // String fields
        builder.groupId(doc.get("groupId"));
        builder.artifactId(doc.get("artifactId"));
        builder.version(doc.get("version"));
        builder.artifactType(doc.get("artifactType"));
        builder.name(doc.get("name"));
        builder.description(doc.get("description"));
        builder.owner(doc.get("owner"));
        builder.modifiedBy(doc.get("modifiedBy"));

        // State
        String state = doc.get("state");
        if (state != null) {
            builder.state(VersionState.valueOf(state));
        }

        // Timestamps (stored as string millis → Date)
        String createdOn = doc.get("createdOn");
        if (createdOn != null) {
            builder.createdOn(new Date(Long.parseLong(createdOn)));
        }

        String modifiedOn = doc.get("modifiedOn");
        if (modifiedOn != null) {
            builder.modifiedOn(new Date(Long.parseLong(modifiedOn)));
        }

        // VersionOrder (stored as numeric)
        if (doc.getField("versionOrder") != null
                && doc.getField("versionOrder").numericValue() != null) {
            builder.versionOrder(doc.getField("versionOrder").numericValue().intValue());
        }

        // Labels (stored as JSON)
        String labelsJson = doc.get("labels_json");
        Map<String, String> labels = documentBuilder.deserializeLabels(labelsJson);
        builder.labels(labels);

        return builder.build();
    }
}
