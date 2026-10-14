package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.error.WebhookStorageNotSupportedException;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Webhook subscriptions are SQL-only. KafkaSQL must refuse every operation and must never fall
 * through to its local SQL replica, which is not journaled and would diverge between nodes.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlWebhookSubscriptionTest {

    @Inject
    KafkaSqlRegistryStorage storage;

    /** The local replica KafkaSQL reads from. Webhook rows must never appear here. */
    @Inject
    SqlRegistryStorage replica;

    @Test
    void webhookManagementIsNotSupported() {
        assertFalse(storage.isWebhookManagementSupported());
    }

    @Test
    void everyWebhookOperationIsRefusedAndNothingReachesTheReplica() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId(id)
                .ownerId("admin")
                .endpointUrl("https://hooks.example.com/registry")
                .eventTypes("[]")
                .createdOn(now)
                .modifiedOn(now)
                .build();

        List<Executable> operations = List.of(
                () -> storage.createWebhookSubscription(subscription),
                () -> storage.getWebhookSubscription(id),
                () -> storage.searchWebhookSubscriptions(0, 20),
                () -> storage.updateWebhookSubscription(id, 1L, subscription),
                () -> storage.softDeleteWebhookSubscription(id, 1L));
        for (Executable operation : operations) {
            WebhookStorageNotSupportedException ex = assertThrows(WebhookStorageNotSupportedException.class,
                    operation);
            assertEquals("Webhook management requires SQL storage.", ex.getMessage());
        }

        assertEquals(0L, replica.searchWebhookSubscriptions(0, 20).getCount());
    }
}
