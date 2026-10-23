package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.storage.impl.sql.repositories.SqlUsageRepository.ClientVersionUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUsageRepositoryTest {

    @Test
    void foldsPerClientRowsIntoOneSummaryPerVersionInVersionOrder() {
        List<SchemaUsageSummaryDto> result = SqlUsageRepository.foldByVersion(List.of(
                new ClientVersionUsage("1", 11, "analytics", 4, 500, 5000),
                new ClientVersionUsage("1", 11, "payments", 6, 1000, 9000),
                new ClientVersionUsage("2", 12, "payments", 1, 7000, 7000)));

        assertEquals(2, result.size());

        SchemaUsageSummaryDto v1 = result.get(0);
        assertEquals("1", v1.getVersion());
        assertEquals(11, v1.getGlobalId());
        assertEquals(10, v1.getTotalFetches());
        assertEquals(2, v1.getUniqueClients());
        assertEquals(500, v1.getFirstFetchedOn());
        assertEquals(9000, v1.getLastFetchedOn());
        assertEquals("analytics,payments", v1.getClientList());

        SchemaUsageSummaryDto v2 = result.get(1);
        assertEquals("2", v2.getVersion());
        assertEquals(1, v2.getTotalFetches());
        assertEquals(1, v2.getUniqueClients());
        assertEquals("payments", v2.getClientList());
    }

    @Test
    void keepsEveryClientWhenThereAreManyOfThem() {
        List<ClientVersionUsage> rows = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rows.add(new ClientVersionUsage("1", 21, "payments-service-client-" + i, 1, 1000, 1000));
        }

        SchemaUsageSummaryDto summary = SqlUsageRepository.foldByVersion(rows).get(0);

        assertEquals(200, summary.getUniqueClients());
        assertEquals(200, summary.getClientList().split(",").length);
        assertTrue(summary.getClientList().length() > 1024,
                "the list must not be cut at the MySQL group_concat_max_len default");
    }

    @Test
    void noRowsGiveNoSummaries() {
        assertEquals(List.of(), SqlUsageRepository.foldByVersion(List.of()));
    }
}
