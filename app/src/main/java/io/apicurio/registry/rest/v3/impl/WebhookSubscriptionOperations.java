package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.CreatedResourceContext;
import io.apicurio.registry.rest.WebhookRequestTooLargeException;
import io.apicurio.registry.rest.WebhookSubscriptionValidationException;
import io.apicurio.registry.rest.v3.beans.CreateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.UpdateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookSubscriptionSearchResults;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.error.WebhookStorageNotSupportedException;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.webhooks.HostResolver;
import io.apicurio.registry.webhooks.WebhookEventTypesJson;
import io.apicurio.registry.webhooks.WebhookRuntime;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.normalizeGroupFilter;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateArtifactIdFilter;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateArtifactTypeFilter;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateEnabled;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateEndpointUrl;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateEventTypes;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateExpectedRevision;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateLimit;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateName;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateOffset;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateOwnerId;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateResolvedEndpoint;
import static io.apicurio.registry.webhooks.WebhookSubscriptionValidator.validateSigningSecretRef;

/**
 * The webhook subscription operations behind {@link AdminResourceImpl}. Authorization and auditing are
 * applied by the annotations on the {@link AdminResourceImpl} methods; this class runs, in order, the
 * storage capability check, structural validation, the outbound DNS check (create only), the activation
 * check, and a single storage call.
 */
@ApplicationScoped
public class WebhookSubscriptionOperations {

    static final String ACTIVATION_NOT_ALLOWED_MESSAGE = "A webhook subscription can only be enabled when "
            + "webhook delivery is available and its signing key is configured.";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory artifactTypes;

    @Inject
    HostResolver hostResolver;

    @Inject
    WebhookRuntime runtime;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    CreatedResourceContext createdResourceContext;

    @Inject
    WebhookRequestBodyContext bodyContext;

    WebhookSubscriptionSearchResults list(BigInteger offset, BigInteger limit) {
        requireWebhookStorage();
        int validOffset = validateOffset(offset);
        int validLimit = validateLimit(limit);
        return V3ApiUtil.dtoToWebhookSubscriptionSearchResults(
                storage.searchWebhookSubscriptions(validOffset, validLimit));
    }

    WebhookSubscription create(CreateWebhookSubscription data) {
        requireWebhookStorage();
        checkRequestBody(data);
        validateEndpointUrl(data.getEndpointUrl());
        validateEventTypes(data.getEventTypes());
        validateName(data.getName());
        String groupFilter = normalizeGroupFilter(data.getGroupFilter());
        validateArtifactIdFilter(data.getArtifactIdFilter());
        validateArtifactTypeFilter(data.getArtifactTypeFilter(), artifactTypes.getAllArtifactTypes());
        validateSigningSecretRef(data.getSigningSecretRef());
        String ownerId = securityIdentity.getPrincipal().getName();
        validateOwnerId(ownerId);

        validateResolvedEndpoint(data.getEndpointUrl(), hostResolver);

        boolean enabled = Boolean.TRUE.equals(data.getEnabled());
        requireActivationAllowed(enabled, data.getSigningSecretRef());

        long now = System.currentTimeMillis();
        WebhookSubscriptionDto created = storage.createWebhookSubscription(WebhookSubscriptionDto.builder()
                .subscriptionId(UUID.randomUUID().toString())
                .name(data.getName())
                .ownerId(ownerId)
                .endpointUrl(data.getEndpointUrl())
                .eventTypes(WebhookEventTypesJson.toJson(data.getEventTypes()))
                .groupFilter(groupFilter)
                .artifactIdFilter(data.getArtifactIdFilter())
                .artifactTypeFilter(data.getArtifactTypeFilter())
                .enabled(enabled)
                .signingSecretRef(data.getSigningSecretRef())
                .createdOn(now)
                .modifiedOn(now)
                .build());
        createdResourceContext.setCreatedId(created.getSubscriptionId());
        return V3ApiUtil.dtoToWebhookSubscription(created);
    }

    WebhookSubscription get(String subscriptionId) {
        requireWebhookStorage();
        requireCanonicalId(subscriptionId);
        return V3ApiUtil.dtoToWebhookSubscription(storage.getWebhookSubscription(subscriptionId));
    }

    WebhookSubscription update(String subscriptionId, UpdateWebhookSubscription data) {
        requireWebhookStorage();
        requireCanonicalId(subscriptionId);
        checkRequestBody(data);
        validateEventTypes(data.getEventTypes());
        validateEnabled(data.getEnabled());
        long expectedRevision = validateExpectedRevision(data.getExpectedRevision());
        validateName(data.getName());
        String groupFilter = normalizeGroupFilter(data.getGroupFilter());
        validateArtifactIdFilter(data.getArtifactIdFilter());
        validateArtifactTypeFilter(data.getArtifactTypeFilter(), artifactTypes.getAllArtifactTypes());

        if (data.getEnabled()) {
            // The signing key reference cannot change, so the stored one decides.
            String signingSecretRef = storage.getWebhookSubscription(subscriptionId).getSigningSecretRef();
            requireActivationAllowed(true, signingSecretRef);
        }

        WebhookSubscriptionDto updated = storage.updateWebhookSubscription(subscriptionId, expectedRevision,
                WebhookSubscriptionDto.builder()
                        .name(data.getName())
                        .eventTypes(WebhookEventTypesJson.toJson(data.getEventTypes()))
                        .groupFilter(groupFilter)
                        .artifactIdFilter(data.getArtifactIdFilter())
                        .artifactTypeFilter(data.getArtifactTypeFilter())
                        .enabled(data.getEnabled())
                        .build());
        return V3ApiUtil.dtoToWebhookSubscription(updated);
    }

    void delete(String subscriptionId, BigInteger expectedRevision) {
        requireWebhookStorage();
        requireCanonicalId(subscriptionId);
        storage.softDeleteWebhookSubscription(subscriptionId, validateExpectedRevision(expectedRevision));
    }

    /**
     * Rejects a body that {@link WebhookRequestBodyReaderInterceptor} found unusable: too large (413), not
     * a JSON object of the right field types (400), or carrying fields the request does not declare (400).
     * Unknown field names are client-supplied, so only the two immutable fields, whose names are fixed, are
     * named in the message.
     */
    private void checkRequestBody(Object data) {
        if (bodyContext.isTooLarge()) {
            throw new WebhookRequestTooLargeException(WebhookRequestBodyReaderInterceptor.MAX_BODY_BYTES);
        }
        if (data == null || bodyContext.isMalformed()) {
            throw new WebhookSubscriptionValidationException(
                    "body: must be a JSON object matching the request schema");
        }
        Set<String> unrecognized = bodyContext.getUnrecognizedFields();
        for (String immutable : List.of("endpointUrl", "signingSecretRef")) {
            if (unrecognized.contains(immutable)) {
                throw new WebhookSubscriptionValidationException(
                        immutable + ": cannot be changed; create a new subscription instead");
            }
        }
        if (!unrecognized.isEmpty()) {
            throw new WebhookSubscriptionValidationException("body: contains a field that is not allowed");
        }
    }

    private void requireWebhookStorage() {
        if (!storage.isWebhookManagementSupported()) {
            throw new WebhookStorageNotSupportedException();
        }
    }

    /**
     * IDs are server-generated canonical UUIDs. Anything else cannot exist, so it gets the same 404 as a
     * missing subscription. The round-trip check matters because {@link UUID#fromString} also accepts
     * non-canonical forms such as {@code "1-1-1-1-1"}.
     */
    private static void requireCanonicalId(String subscriptionId) {
        try {
            if (UUID.fromString(subscriptionId).toString().equals(subscriptionId)) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID at all: fall through to the 404.
        }
        throw new WebhookSubscriptionNotFoundException();
    }

    private void requireActivationAllowed(boolean enabled, String signingSecretRef) {
        if (enabled && (!runtime.isAvailable() || signingSecretRef == null
                || !runtime.signingKeyResolves(signingSecretRef))) {
            throw new ConflictException(ACTIVATION_NOT_ALLOWED_MESSAGE);
        }
    }
}
