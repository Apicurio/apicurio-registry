package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.error.WebhookSubscriptionRevisionConflictException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookSubscriptionDtoMapper;
import io.apicurio.registry.types.RegistryException;
import org.slf4j.Logger;

import java.util.List;

/**
 * Repository handling webhook subscription operations in the SQL storage layer.
 * <p>
 * Subscriptions are soft-deleted: a deleted row keeps its data but is invisible to every read. Updates
 * and deletes are conditional on the caller's expected revision (optimistic concurrency).
 */
public class SqlWebhookSubscriptionRepository {

    private final Logger log;

    private final SqlStatements sqlStatements;

    private final HandleFactory handles;

    public SqlWebhookSubscriptionRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    /**
     * Create a new webhook subscription. The stored row always starts at revision 1 and not deleted,
     * regardless of what the given DTO carries; the returned DTO reflects the stored row.
     */
    public WebhookSubscriptionDto createWebhookSubscription(WebhookSubscriptionDto subscription)
            throws RegistryStorageException {
        log.debug("Inserting a webhook subscription row for: {}", subscription.getSubscriptionId());
        return handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.insertWebhookSubscription())
                    .bind(0, subscription.getSubscriptionId())
                    .bind(1, subscription.getName())
                    .bind(2, subscription.getOwnerId())
                    .bind(3, subscription.getEndpointUrl())
                    .bind(4, subscription.getEventTypes())
                    .bind(5, subscription.getGroupFilter())
                    .bind(6, subscription.getArtifactIdFilter())
                    .bind(7, subscription.getArtifactTypeFilter())
                    .bind(8, subscription.isEnabled())
                    .bind(9, subscription.getSigningSecretRef())
                    .bind(10, subscription.getCreatedOn())
                    .bind(11, subscription.getModifiedOn())
                    .execute();
            return selectActive(handle, subscription.getSubscriptionId());
        });
    }

    /**
     * Get an active webhook subscription by ID.
     */
    public WebhookSubscriptionDto getWebhookSubscription(String subscriptionId)
            throws RegistryStorageException {
        log.debug("Selecting a single webhook subscription for: {}", subscriptionId);
        return handles.withHandle(handle -> selectActive(handle, subscriptionId));
    }

    /**
     * Search active webhook subscriptions with pagination, ordered by creation time then ID.
     */
    public WebhookSubscriptionSearchResultsDto searchWebhookSubscriptions(int offset, int limit)
            throws RegistryStorageException {
        log.debug("Searching webhook subscriptions.");
        return handles.withHandleNoException(handle -> {
            String query = sqlStatements.selectWebhookSubscriptions();
            if ("mssql".equals(sqlStatements.dbType())) {
                query += " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            } else {
                query += " LIMIT ? OFFSET ?";
            }

            var subscriptionsQuery = handle.createQuery(query);
            if ("mssql".equals(sqlStatements.dbType())) {
                subscriptionsQuery.bind(0, offset).bind(1, limit);
            } else {
                subscriptionsQuery.bind(0, limit).bind(1, offset);
            }
            List<WebhookSubscriptionDto> subscriptions = subscriptionsQuery
                    .map(WebhookSubscriptionDtoMapper.instance)
                    .list();

            Integer count = handle.createQuery(sqlStatements.countWebhookSubscriptions())
                    .mapTo(Integer.class)
                    .one();
            return WebhookSubscriptionSearchResultsDto.builder()
                    .count(count)
                    .subscriptions(subscriptions)
                    .build();
        });
    }

    /**
     * Replace the mutable fields of an active subscription, if its current revision equals
     * {@code expectedRevision}. The owner, endpoint URL, signing secret reference and creation time never
     * change; the matching fields of {@code subscription} are ignored. Returns the updated row.
     */
    public WebhookSubscriptionDto updateWebhookSubscription(String subscriptionId, long expectedRevision,
            WebhookSubscriptionDto subscription) throws RegistryStorageException {
        log.debug("Updating webhook subscription: {}", subscriptionId);
        return handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateWebhookSubscription())
                    .bind(0, subscription.getName())
                    .bind(1, subscription.getEventTypes())
                    .bind(2, subscription.getGroupFilter())
                    .bind(3, subscription.getArtifactIdFilter())
                    .bind(4, subscription.getArtifactTypeFilter())
                    .bind(5, subscription.isEnabled())
                    .bind(6, System.currentTimeMillis())
                    .bind(7, subscriptionId)
                    .bind(8, expectedRevision)
                    .execute();
            if (rowCount == 0) {
                throw notFoundOrConflict(handle, subscriptionId);
            }
            // #8569: cancel undispatched webhook_delivery_logs rows here when enabled becomes false
            return selectActive(handle, subscriptionId);
        });
    }

    /**
     * Soft-delete an active subscription, if its current revision equals {@code expectedRevision}.
     * The row is kept, disabled, and hidden from all reads.
     */
    public void softDeleteWebhookSubscription(String subscriptionId, long expectedRevision)
            throws RegistryStorageException {
        log.debug("Soft-deleting webhook subscription: {}", subscriptionId);
        handles.withHandle(handle -> {
            long now = System.currentTimeMillis();
            int rowCount = handle.createUpdate(sqlStatements.softDeleteWebhookSubscription())
                    .bind(0, now)
                    .bind(1, false)
                    .bind(2, now)
                    .bind(3, subscriptionId)
                    .bind(4, expectedRevision)
                    .execute();
            if (rowCount == 0) {
                throw notFoundOrConflict(handle, subscriptionId);
            }
            // #8569: cancel undispatched webhook_delivery_logs rows here
            return null;
        });
    }

    private WebhookSubscriptionDto selectActive(Handle handle, String subscriptionId) {
        return handle.createQuery(sqlStatements.selectWebhookSubscriptionById())
                .bind(0, subscriptionId)
                .map(WebhookSubscriptionDtoMapper.instance)
                .findOne()
                .orElseThrow(WebhookSubscriptionNotFoundException::new);
    }

    /**
     * A conditional write touched no rows: either the subscription is missing/deleted (404) or it
     * exists with a different revision (409).
     */
    private RegistryException notFoundOrConflict(Handle handle, String subscriptionId) {
        int active = handle.createQuery(sqlStatements.selectActiveWebhookSubscriptionCountById())
                .bind(0, subscriptionId)
                .mapTo(Integer.class)
                .one();
        if (active == 0) {
            return new WebhookSubscriptionNotFoundException();
        }
        return new WebhookSubscriptionRevisionConflictException();
    }
}
