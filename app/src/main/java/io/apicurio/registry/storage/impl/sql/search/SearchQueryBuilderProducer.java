package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

/**
 * CDI producer for SearchQueryBuilder instances.
 * <p>
 * Returns the appropriate implementation based on the database type and configuration.
 */
@ApplicationScoped
public class SearchQueryBuilderProducer {

    @Inject
    SqlStatements sqlStatements;

    @ConfigProperty(name = "apicurio.search.trigram.enabled", defaultValue = "false")
    @Info(category = CATEGORY_STORAGE, description = "Enable PostgreSQL trigram-based case-insensitive search. When enabled, substring searches on PostgreSQL use ILIKE instead of LIKE, providing case-insensitive matching optimized by pg_trgm GIN indexes.", availableSince = "3.1.7")
    boolean trigramEnabled;

    @Produces
    @Singleton
    public SearchQueryBuilder produceSearchQueryBuilder() {
        String dbType = sqlStatements.dbType();
        return switch (dbType) {
            case "postgresql" -> new PostgreSQLSearchQueryBuilder(sqlStatements, trigramEnabled);
            case "mssql" -> new SQLServerSearchQueryBuilder(sqlStatements);
            default -> new CommonSearchQueryBuilder(sqlStatements);
        };
    }
}
