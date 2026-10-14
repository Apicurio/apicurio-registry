package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionSearchResultsDto;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.error.WebhookSubscriptionRevisionConflictException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises webhook subscription storage through the injected {@code @Current} storage, so the whole
 * decorator chain in front of the SQL implementation is included.
 */
@QuarkusTest
public class SqlWebhookSubscriptionStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void sqlStorageSupportsWebhookManagement() {
        assertTrue(storage.isWebhookManagementSupported());
    }

    @Test
    void subscriptionLifecycleThroughStorage() {
        String id = UUID.randomUUID().toString();
        long countBefore = storage.searchWebhookSubscriptions(0, 100).getCount();

        WebhookSubscriptionDto created = storage.createWebhookSubscription(subscription(id, "first"));
        assertEquals(1L, created.getRevision());
        assertEquals("first", storage.getWebhookSubscription(id).getName());

        WebhookSubscriptionSearchResultsDto afterCreate = storage.searchWebhookSubscriptions(0, 100);
        assertEquals(countBefore + 1, afterCreate.getCount());
        assertTrue(afterCreate.getSubscriptions().stream().anyMatch(s -> id.equals(s.getSubscriptionId())));

        WebhookSubscriptionDto updated = storage.updateWebhookSubscription(id, 1L, subscription(id, "second"));
        assertEquals(2L, updated.getRevision());
        assertEquals("second", updated.getName());

        assertThrows(WebhookSubscriptionRevisionConflictException.class,
                () -> storage.updateWebhookSubscription(id, 1L, subscription(id, "stale")));
        assertThrows(WebhookSubscriptionRevisionConflictException.class,
                () -> storage.softDeleteWebhookSubscription(id, 1L));
        assertEquals("second", storage.getWebhookSubscription(id).getName());

        storage.softDeleteWebhookSubscription(id, 2L);

        assertThrows(WebhookSubscriptionNotFoundException.class, () -> storage.getWebhookSubscription(id));
        WebhookSubscriptionSearchResultsDto afterDelete = storage.searchWebhookSubscriptions(0, 100);
        assertEquals(countBefore, afterDelete.getCount());
        assertFalse(afterDelete.getSubscriptions().stream().anyMatch(s -> id.equals(s.getSubscriptionId())));
    }

    private static WebhookSubscriptionDto subscription(String id, String name) {
        long now = System.currentTimeMillis();
        return WebhookSubscriptionDto.builder()
                .subscriptionId(id)
                .name(name)
                .ownerId("admin")
                .endpointUrl("https://hooks.example.com/registry")
                .eventTypes("[]")
                .enabled(false)
                .createdOn(now)
                .modifiedOn(now)
                .build();
    }
}
