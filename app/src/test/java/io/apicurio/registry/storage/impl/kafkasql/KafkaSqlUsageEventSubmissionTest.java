package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteOldUsageEvents1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.RecordUsageEvent1Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Pins the routing of the two usage-event writes. Nothing waits on either message, so sending
 * them through submitMessage would register a coordinator entry that nobody ever removes. The
 * submitter tests cover what submitFireAndForget does; this covers that these callers use it.
 */
class KafkaSqlUsageEventSubmissionTest {

    private KafkaSqlSubmitter submitter;

    private KafkaSqlRegistryStorage storage;

    @BeforeEach
    void setup() {
        submitter = mock(KafkaSqlSubmitter.class);
        storage = new KafkaSqlRegistryStorage();
        storage.submitter = submitter;
    }

    @Test
    void testRecordUsageEventIsSubmittedFireAndForget() {
        storage.recordUsageEvent(new SchemaUsageEventDto(11L, 22L, "client-a", "FETCH", 1_000L));

        verify(submitter).submitFireAndForget(
                new RecordUsageEvent1Message(11L, 22L, "client-a", "FETCH", 1_000L));
        verifyNoMoreInteractions(submitter);
    }

    @Test
    void testDeleteOldUsageEventsIsSubmittedFireAndForget() {
        storage.deleteOldUsageEvents(5_000L);

        verify(submitter).submitFireAndForget(new DeleteOldUsageEvents1Message(5_000L));
        verifyNoMoreInteractions(submitter);
    }
}
