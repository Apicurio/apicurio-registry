package io.apicurio.registry.webhooks;

import io.apicurio.registry.events.dto.WebhookEventTypes;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.WebhookSubscriptionValidationException;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validation of webhook subscription requests.
 * <p>
 * Every failure is a {@link WebhookSubscriptionValidationException} whose message is {@code "<field>: <rule>"}.
 * Messages never contain the submitted value: they reach API clients and the audit log, and an endpoint URL
 * or filter can carry information the caller did not intend to log.
 */
public final class WebhookSubscriptionValidator {

    public static final int MAX_ENDPOINT_URL_LENGTH = 2048;
    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    public static final int MAX_EVENT_TYPES = 5;
    public static final int MAX_NAME_LENGTH = 512;
    public static final int MAX_GROUP_FILTER_LENGTH = 512;
    public static final int MAX_ARTIFACT_ID_FILTER_LENGTH = 512;
    public static final int MAX_ARTIFACT_TYPE_FILTER_LENGTH = 32;
    public static final int MAX_SIGNING_SECRET_REF_LENGTH = 512;
    public static final int MAX_OWNER_ID_LENGTH = 256;
    public static final int MAX_LIMIT = 100;

    private static final Pattern SIGNING_SECRET_REF_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final String ENDPOINT_URL = "endpointUrl";
    private static final String EVENT_TYPES = "eventTypes";
    private static final String EXPECTED_REVISION = "expectedRevision";
    private static final String IS_REQUIRED = "is required";
    private static final String MUST_BE_AT_MOST = "must be at most ";
    private static final String CHARACTERS = " characters";

    private WebhookSubscriptionValidator() {
    }

    /**
     * Checks an endpoint URL: an absolute {@code https} URL with a host, a port (when given) in 1-65535,
     * no user information, no fragment, and a host that is not a forbidden IP literal. DNS is not consulted here.
     */
    public static void validateEndpointUrl(String endpointUrl) {
        if (endpointUrl == null) {
            throw invalid(ENDPOINT_URL, IS_REQUIRED);
        }
        if (endpointUrl.length() > MAX_ENDPOINT_URL_LENGTH) {
            throw invalid(ENDPOINT_URL, MUST_BE_AT_MOST + MAX_ENDPOINT_URL_LENGTH + CHARACTERS);
        }
        URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (URISyntaxException e) {
            throw invalid(ENDPOINT_URL, "must be a valid URL");
        }
        if (!uri.isAbsolute() || !"https".equals(uri.getScheme())) {
            throw invalid(ENDPOINT_URL, "must be an absolute https URL");
        }
        if (uri.getHost() == null) {
            throw invalid(ENDPOINT_URL, "must include a host");
        }
        // getPort() is -1 when the URL has no port, which means the https default.
        int port = uri.getPort();
        if (port != -1 && (port < MIN_PORT || port > MAX_PORT)) {
            throw invalid(ENDPOINT_URL, "must use a port between " + MIN_PORT + " and " + MAX_PORT);
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid(ENDPOINT_URL, "must not contain user information");
        }
        if (uri.getRawFragment() != null) {
            throw invalid(ENDPOINT_URL, "must not contain a fragment");
        }
        if (OutboundAddressPolicy.isForbiddenLiteral(uri.getHost())) {
            throw invalid(ENDPOINT_URL, "must not point to a loopback, private, link-local or other internal address");
        }
    }

    /**
     * Checks that every address the endpoint host resolves to is allowed. Call only after
     * {@link #validateEndpointUrl(String)} has passed.
     */
    public static void validateResolvedEndpoint(String endpointUrl, HostResolver resolver) {
        String host = URI.create(endpointUrl).getHost();
        if (!OutboundAddressPolicy.allResolvedAddressesAllowed(host, resolver)) {
            throw invalid(ENDPOINT_URL, "must resolve only to public addresses");
        }
    }

    public static void validateEventTypes(List<String> eventTypes) {
        if (eventTypes == null) {
            throw invalid(EVENT_TYPES, IS_REQUIRED);
        }
        if (eventTypes.size() > MAX_EVENT_TYPES) {
            throw invalid(EVENT_TYPES, "must contain at most " + MAX_EVENT_TYPES + " values");
        }
        Set<String> seen = new HashSet<>();
        for (String eventType : eventTypes) {
            if (WebhookEventTypes.isReserved(eventType)) {
                throw invalid(EVENT_TYPES, "contains an event type that is reserved and not supported yet");
            }
            if (WebhookEventTypes.fromSupportedValue(eventType).isEmpty()) {
                throw invalid(EVENT_TYPES, "contains an unsupported event type");
            }
            if (!seen.add(eventType)) {
                throw invalid(EVENT_TYPES, "must not contain duplicates");
            }
        }
    }

    public static void validateName(String name) {
        if (name == null) {
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw invalid("name", MUST_BE_AT_MOST + MAX_NAME_LENGTH + CHARACTERS);
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("name", "must not contain control characters");
        }
    }

    /**
     * Checks a group filter and returns it in the registry's raw form (so {@code "default"} in any case
     * becomes the default group's raw ID), or {@code null} when no filter is set.
     */
    public static String normalizeGroupFilter(String groupFilter) {
        if (groupFilter == null) {
            return null;
        }
        // GroupId's own error message echoes the value, so check validity before constructing it.
        if (groupFilter.length() > MAX_GROUP_FILTER_LENGTH || !GroupId.isValid(groupFilter)) {
            throw invalid("groupFilter", "must be 1 to " + MAX_GROUP_FILTER_LENGTH
                    + " characters without line breaks");
        }
        return new GroupId(groupFilter).getRawGroupId();
    }

    public static void validateArtifactIdFilter(String artifactIdFilter) {
        if (artifactIdFilter != null && artifactIdFilter.length() > MAX_ARTIFACT_ID_FILTER_LENGTH) {
            throw invalid("artifactIdFilter", MUST_BE_AT_MOST + MAX_ARTIFACT_ID_FILTER_LENGTH + CHARACTERS);
        }
    }

    public static void validateArtifactTypeFilter(String artifactTypeFilter, Collection<String> artifactTypes) {
        if (artifactTypeFilter == null) {
            return;
        }
        if (artifactTypeFilter.length() > MAX_ARTIFACT_TYPE_FILTER_LENGTH
                || !artifactTypes.contains(artifactTypeFilter)) {
            throw invalid("artifactTypeFilter", "must be one of the registry's artifact types");
        }
    }

    public static void validateSigningSecretRef(String signingSecretRef) {
        if (signingSecretRef == null) {
            return;
        }
        if (signingSecretRef.length() > MAX_SIGNING_SECRET_REF_LENGTH
                || !SIGNING_SECRET_REF_PATTERN.matcher(signingSecretRef).matches()) {
            throw invalid("signingSecretRef", "must be 1 to " + MAX_SIGNING_SECRET_REF_LENGTH
                    + " characters from A-Z, a-z, 0-9, '.', '_' and '-'");
        }
    }

    public static void validateEnabled(Boolean enabled) {
        if (enabled == null) {
            throw invalid("enabled", IS_REQUIRED);
        }
    }

    public static long validateExpectedRevision(Long expectedRevision) {
        if (expectedRevision == null) {
            throw invalid(EXPECTED_REVISION, IS_REQUIRED);
        }
        if (expectedRevision < 1) {
            throw invalid(EXPECTED_REVISION, "must be at least 1");
        }
        return expectedRevision;
    }

    public static long validateExpectedRevision(BigInteger expectedRevision) {
        if (expectedRevision == null) {
            throw invalid(EXPECTED_REVISION, IS_REQUIRED);
        }
        if (expectedRevision.signum() < 1 || expectedRevision.bitLength() >= Long.SIZE) {
            throw invalid(EXPECTED_REVISION, "must be between 1 and " + Long.MAX_VALUE);
        }
        return expectedRevision.longValueExact();
    }

    /**
     * Returns the offset as an int; {@code null} means 0.
     */
    public static int validateOffset(BigInteger offset) {
        if (offset == null) {
            return 0;
        }
        if (offset.signum() < 0 || offset.bitLength() >= Integer.SIZE) {
            throw invalid("offset", "must be between 0 and " + Integer.MAX_VALUE);
        }
        return offset.intValueExact();
    }

    /**
     * Returns the limit as an int; {@code null} means 20.
     */
    public static int validateLimit(BigInteger limit) {
        if (limit == null) {
            return 20;
        }
        if (limit.compareTo(BigInteger.ONE) < 0 || limit.compareTo(BigInteger.valueOf(MAX_LIMIT)) > 0) {
            throw invalid("limit", "must be between 1 and " + MAX_LIMIT);
        }
        return limit.intValueExact();
    }

    public static void validateOwnerId(String ownerId) {
        if (ownerId.length() > MAX_OWNER_ID_LENGTH) {
            throw invalid("ownerId", "the caller's principal name must be at most " + MAX_OWNER_ID_LENGTH
                    + CHARACTERS);
        }
    }

    private static WebhookSubscriptionValidationException invalid(String field, String rule) {
        return new WebhookSubscriptionValidationException(field + ": " + rule);
    }
}
