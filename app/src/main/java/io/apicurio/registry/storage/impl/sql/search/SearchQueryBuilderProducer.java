package io.apicurio.registry.storage.impl.sql.search;

import io.apicurio.registry.storage.impl.sql.SqlStatements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * CDI producer for SearchQueryBuilder instances.
 * <p>
 * Returns the appropriate implementation based on the database type.
 */
@ApplicationScoped
public class SearchQueryBuilderProducer {

    @Inject
    SqlStatements sqlStatements;

    @Produces
    @Singleton
    public SearchQueryBuilder produceSearchQueryBuilder() {
        String dbType = sqlStatements.dbType();
        return switch (dbType) {
            case "mssql" -> new SQLServerSearchQueryBuilder(sqlStatements);
            default -> new CommonSearchQueryBuilder(sqlStatements);
        };
    }
}
