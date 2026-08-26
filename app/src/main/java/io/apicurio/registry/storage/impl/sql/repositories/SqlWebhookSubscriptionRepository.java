package io.apicurio.registry.storage.impl.sql.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.WebhookSubscriptionDtoMapper;
import org.slf4j.Logger;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;


public class SqlWebhookSubscriptionRepository {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Logger log;
    private final SqlStatements sqlStatements;
    private final HandleFactory handles;

    public SqlWebhookSubscriptionRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    public void createWebhookSubscription(WebhookSubscriptionDto subscription)
            throws RegistryStorageException {

        log.debug("Inserting webhook subscription: {}", subscription.getSubscriptionId());

        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.insertWebhookSubscription())
                    .bind(0, subscription.getSubscriptionId())
                    .bind(1, subscription.getEndpointUrl())
                    .bind(2, serializeEventTypes(subscription.getEventTypes()))
                    .bind(3, subscription.getGroupFilter())
                    .bind(4, subscription.getArtifactFilter())
                    .bind(5, subscription.getAuthType())
                    .bind(6, subscription.getAuthConfig())
                    .bind(7, subscription.isEnabled())
                    .bind(8, subscription.getOwner())
                    .bind(9, new Timestamp(subscription.getCreatedOn()))
                    .bind(10, subscription.getModifiedBy())
                    .bind(11, subscription.getModifiedOn() > 0
                            ? new Timestamp(subscription.getModifiedOn())
                            : null)
                    .execute();
            return null;
        });
    }

    public void deleteWebhookSubscription(String subscriptionId)
            throws RegistryStorageException {

        log.debug("Deleting webhook subscription: {}", subscriptionId);

        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deleteWebhookSubscription())
                    .bind(0, subscriptionId)
                    .execute();

            if (rowCount == 0) {
                throw new RegistryStorageException(
                        "Webhook subscription not found: " + subscriptionId);
            }

            return null;
        });
    }

    public WebhookSubscriptionDto getWebhookSubscription(String subscriptionId)
            throws RegistryStorageException {

        log.debug("Selecting webhook subscription: {}", subscriptionId);

        return handles.withHandle(handle -> {
            Optional<WebhookSubscriptionDto> result =
                    handle.createQuery(sqlStatements.selectWebhookSubscriptionById())
                            .bind(0, subscriptionId)
                            .map(WebhookSubscriptionDtoMapper.instance)
                            .findOne();

           return result.orElseThrow(() ->
        new WebhookSubscriptionNotFoundException(subscriptionId));
        });
    }

    public WebhookSubscriptionSearchResultsDto searchWebhookSubscriptions(int offset, int limit)
            throws RegistryStorageException {

        log.debug("Searching webhook subscriptions.");

        return handles.withHandleNoException(handle -> {
            String query = sqlStatements.selectWebhookSubscriptions() + " LIMIT ? OFFSET ?";
            String countQuery = sqlStatements.countWebhookSubscriptions();

            List<WebhookSubscriptionDto> subscriptions =
                    handle.createQuery(query)
                            .bind(0, limit)
                            .bind(1, offset)
                            .map(WebhookSubscriptionDtoMapper.instance)
                            .list();

            Integer count = handle.createQuery(countQuery)
                    .mapTo(Integer.class)
                    .one();

            return WebhookSubscriptionSearchResultsDto.builder()
                    .webhookSubscriptions(subscriptions)
                    .count(count)
                    .build();
        });
    }

    private String serializeEventTypes(List<String> eventTypes) throws RegistryStorageException {
        try {
            return objectMapper.writeValueAsString(eventTypes);
        } catch (Exception e) {
            throw new RegistryStorageException("Failed to serialize webhook eventTypes.", e);
        }
    }

    public void updateWebhookSubscription(WebhookSubscriptionDto subscription)
            throws RegistryStorageException {

        log.debug("Updating webhook subscription: {}", subscription.getSubscriptionId());

        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateWebhookSubscription())
                    .bind(0, subscription.getEndpointUrl())
                    .bind(1, serializeEventTypes(subscription.getEventTypes()))
                    .bind(2, subscription.getGroupFilter())
                    .bind(3, subscription.getArtifactFilter())
                    .bind(4, subscription.getAuthType())
                    .bind(5, subscription.getAuthConfig())
                    .bind(6, subscription.isEnabled())
                    .bind(7, subscription.getModifiedBy())
                    .bind(8, new Timestamp(subscription.getModifiedOn()))
                    .bind(9, subscription.getSubscriptionId())
                    .execute();

            if (rowCount == 0) {
                throw new RegistryStorageException(
                        "Webhook subscription not found: " + subscription.getSubscriptionId());
            }

            return null;
        });
    }
}
