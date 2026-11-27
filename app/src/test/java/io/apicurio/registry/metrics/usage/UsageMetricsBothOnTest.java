package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.rest.UsageTelemetryBuffer;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * Both switches on. The metric counts every fetch while the SQL buffer sees the same requests, so the
 * deduplication that the buffer applies internally does not change what is exported.
 */
@QuarkusTest
@TestProfile(UsageMetricsBothOnTest.Profile.class)
class UsageMetricsBothOnTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.telemetry.enabled", "true",
                    "apicurio.usage.flush.every", "2s",
                    "apicurio.usage.metrics.enabled", "true");
        }
    }

    @InjectSpy
    UsageTelemetryBuffer buffer;

    @BeforeEach
    void resetSpy() {
        Mockito.clearInvocations(buffer);
    }

    @Test
    void everyFetchIsCountedAndTheSqlBufferStillReceivesTheLegacyEvents() throws Exception {
        Schema schema = createSchema();
        String client = newClient("both-on");

        for (int i = 0; i < 4; i++) {
            assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        }
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId() + "/references", client, "SERIALIZE"));

        awaitSeries(client, Map.of("global_id:" + schema.globalId() + ":SERIALIZE", 4L));
        ArgumentCaptor<SchemaUsageEventDto> events = ArgumentCaptor.forClass(SchemaUsageEventDto.class);
        verify(buffer, Mockito.times(5)).addEvent(events.capture());
        events.getAllValues().forEach(event -> {
            assertEquals(schema.globalId(), event.getGlobalId());
            assertEquals(client, event.getClientId());
        });
    }
}
