package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ElasticsearchSearchServiceTest {

    @Test
    void stateFilterUppercasesUnderTurkishDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            ElasticsearchSearchService service = new ElasticsearchSearchService();

            SearchFilter filter = new SearchFilter();
            filter.setType(SearchFilterType.state);
            filter.setStringValue("disabled");

            Query query = service.buildEsQuery(Set.of(filter));

            String termValue = query.bool().must().get(0).term().value().stringValue();
            assertEquals("DISABLED", termValue);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
