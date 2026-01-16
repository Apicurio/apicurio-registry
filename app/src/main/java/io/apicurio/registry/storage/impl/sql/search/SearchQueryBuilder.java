package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;

import java.util.List;
import java.util.Set;

/**
 * Interface for building database-specific search queries.
 * <p>
 * This abstraction allows different database implementations to optimize
 * search queries based on their specific capabilities:
 * <ul>
 *   <li>PostgreSQL: Can use trigram indexes for substring searches, ILIKE for case-insensitive matching</li>
 *   <li>MySQL: Standard LIKE with B-tree indexes, FULLTEXT possible in future</li>
 *   <li>SQL Server: Standard LIKE, special handling for TEXT columns with GROUP BY</li>
 *   <li>H2: Standard LIKE with B-tree indexes</li>
 * </ul>
 */
public interface SearchQueryBuilder {

    /**
     * The type of entity being searched.
     */
    enum EntityType {
        ARTIFACT,
        VERSION,
        GROUP
    }

    /**
     * Builds a search query with count using window function.
     *
     * @param entityType The type of entity to search
     * @param filters The search filters to apply
     * @param orderBy The field to order by
     * @param orderDirection The order direction (ASC/DESC)
     * @return A SearchQuery containing the SQL and parameter binders
     */
    SearchQuery buildSearchQuery(EntityType entityType, Set<SearchFilter> filters,
            OrderBy orderBy, OrderDirection orderDirection);

    /**
     * Result of building a search query.
     */
    record SearchQuery(String sql, List<SqlStatementVariableBinder> binders) {
    }
}
