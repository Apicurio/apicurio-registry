package io.apicurio.registry.webhooks;

import io.apicurio.registry.rest.WebhookSubscriptionValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookSubscriptionValidatorTest {

    private static final String CREATED = "io.apicurio.registry.artifact.created";
    private static final String DELETED = "io.apicurio.registry.artifact.deleted";

    // ---- endpointUrl ----

    @Test
    void endpointUrl_acceptsAbsoluteHttpsUrlWithPathPortAndQuery() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator
                .validateEndpointUrl("https://hooks.example.com:8443/registry?team=orders"));
    }

    @Test
    void endpointUrl_isRequired() {
        assertMessage("endpointUrl: is required", () -> WebhookSubscriptionValidator.validateEndpointUrl(null));
    }

    @Test
    void endpointUrl_lengthLimitIs2048() {
        String prefix = "https://hooks.example.com/";
        String atLimit = prefix + "a".repeat(2048 - prefix.length());
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateEndpointUrl(atLimit));
        assertMessage("endpointUrl: must be at most 2048 characters",
                () -> WebhookSubscriptionValidator.validateEndpointUrl(atLimit + "a"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://hooks.example.com/x", "HTTPS://hooks.example.com/x", "ftp://hooks.example.com",
            "/relative/path", "hooks.example.com/x" })
    void endpointUrl_mustBeAbsoluteHttps(String url) {
        assertMessage("endpointUrl: must be an absolute https URL",
                () -> WebhookSubscriptionValidator.validateEndpointUrl(url));
    }

    @Test
    void endpointUrl_mustParse() {
        assertMessage("endpointUrl: must be a valid URL",
                () -> WebhookSubscriptionValidator.validateEndpointUrl("https://hooks example.com/x"));
    }

    @Test
    void endpointUrl_mustHaveHost() {
        assertMessage("endpointUrl: must include a host",
                () -> WebhookSubscriptionValidator.validateEndpointUrl("https:///path-only"));
    }

    @Test
    void endpointUrl_mustNotContainUserInfo() {
        assertMessage("endpointUrl: must not contain user information",
                () -> WebhookSubscriptionValidator.validateEndpointUrl("https://user:secret@hooks.example.com/x"));
    }

    @Test
    void endpointUrl_mustNotContainFragment() {
        assertMessage("endpointUrl: must not contain a fragment",
                () -> WebhookSubscriptionValidator.validateEndpointUrl("https://hooks.example.com/x#frag"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://127.0.0.1/x", "https://10.0.0.5/x", "https://169.254.169.254/latest",
            "https://[::1]/x", "https://[::ffff:192.168.1.1]/x", "https://2130706433/x" })
    void endpointUrl_mustNotTargetForbiddenLiteral(String url) {
        assertMessage("endpointUrl: must not point to a loopback, private, link-local or other internal address",
                () -> WebhookSubscriptionValidator.validateEndpointUrl(url));
    }

    @Test
    void resolvedEndpoint_publicAddressesPass() {
        HostResolver resolver = host -> new InetAddress[] { InetAddress.getByName("93.184.216.34") };
        assertDoesNotThrow(() -> WebhookSubscriptionValidator
                .validateResolvedEndpoint("https://hooks.example.com/x", resolver));
    }

    @Test
    void resolvedEndpoint_anyInternalAddressFails() {
        HostResolver resolver = host -> new InetAddress[] { InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("10.1.2.3") };
        assertMessage("endpointUrl: must resolve only to public addresses",
                () -> WebhookSubscriptionValidator.validateResolvedEndpoint("https://hooks.example.com/x", resolver));
    }

    @Test
    void resolvedEndpoint_lookupFailureFails() {
        HostResolver resolver = host -> {
            throw new UnknownHostException(host);
        };
        assertMessage("endpointUrl: must resolve only to public addresses",
                () -> WebhookSubscriptionValidator.validateResolvedEndpoint("https://hooks.example.com/x", resolver));
    }

    // ---- eventTypes ----

    @Test
    void eventTypes_acceptsEmptyAndAllFiveSupported() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateEventTypes(List.of()));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateEventTypes(List.of(CREATED,
                "io.apicurio.registry.artifact.metadata.updated", DELETED,
                "io.apicurio.registry.artifact.version.created",
                "io.apicurio.registry.artifact.version.state.changed")));
    }

    @Test
    void eventTypes_isRequired() {
        assertMessage("eventTypes: is required", () -> WebhookSubscriptionValidator.validateEventTypes(null));
    }

    @Test
    void eventTypes_atMostFive() {
        List<String> six = new ArrayList<>(Collections.nCopies(6, CREATED));
        assertMessage("eventTypes: must contain at most 5 values",
                () -> WebhookSubscriptionValidator.validateEventTypes(six));
    }

    @ParameterizedTest
    @ValueSource(strings = { "io.apicurio.registry.artifact.deprecated", "io.apicurio.registry.rule.violated" })
    void eventTypes_reservedTypesGetTheirOwnMessage(String reserved) {
        assertMessage("eventTypes: contains an event type that is reserved and not supported yet",
                () -> WebhookSubscriptionValidator.validateEventTypes(List.of(CREATED, reserved)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "artifact.created", "IO.APICURIO.REGISTRY.ARTIFACT.CREATED", "unknown", "" })
    void eventTypes_unknownValuesAreRejected(String value) {
        assertMessage("eventTypes: contains an unsupported event type",
                () -> WebhookSubscriptionValidator.validateEventTypes(List.of(value)));
    }

    @Test
    void eventTypes_nullEntryIsRejected() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertMessage("eventTypes: contains an unsupported event type",
                () -> WebhookSubscriptionValidator.validateEventTypes(withNull));
    }

    @Test
    void eventTypes_duplicatesAreRejected() {
        assertMessage("eventTypes: must not contain duplicates",
                () -> WebhookSubscriptionValidator.validateEventTypes(List.of(CREATED, DELETED, CREATED)));
    }

    // ---- name and filters ----

    @Test
    void name_isOptionalAndLimitedTo512() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateName(null));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateName("n".repeat(512)));
        assertMessage("name: must be at most 512 characters",
                () -> WebhookSubscriptionValidator.validateName("n".repeat(513)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "line\nbreak", "tab\tseparated", "nul\u0000char" })
    void name_rejectsControlCharacters(String name) {
        assertMessage("name: must not contain control characters",
                () -> WebhookSubscriptionValidator.validateName(name));
    }

    @Test
    void groupFilter_nullMeansAnyGroup() {
        assertNull(WebhookSubscriptionValidator.normalizeGroupFilter(null));
    }

    @Test
    void groupFilter_keepsOrdinaryGroupsAndNormalizesDefault() {
        assertEquals("com.example", WebhookSubscriptionValidator.normalizeGroupFilter("com.example"));
        assertEquals("__$GROUPID$__", WebhookSubscriptionValidator.normalizeGroupFilter("default"));
        assertEquals("__$GROUPID$__", WebhookSubscriptionValidator.normalizeGroupFilter("DEFAULT"));
    }

    @Test
    void groupFilter_lengthAndContentLimits() {
        assertEquals("g".repeat(512), WebhookSubscriptionValidator.normalizeGroupFilter("g".repeat(512)));
        String rule = "groupFilter: must be 1 to 512 characters without line breaks";
        assertMessage(rule, () -> WebhookSubscriptionValidator.normalizeGroupFilter("g".repeat(513)));
        assertMessage(rule, () -> WebhookSubscriptionValidator.normalizeGroupFilter(""));
        assertMessage(rule, () -> WebhookSubscriptionValidator.normalizeGroupFilter("a\nb"));
    }

    @Test
    void artifactIdFilter_limitedTo512() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateArtifactIdFilter(null));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateArtifactIdFilter("a".repeat(512)));
        assertMessage("artifactIdFilter: must be at most 512 characters",
                () -> WebhookSubscriptionValidator.validateArtifactIdFilter("a".repeat(513)));
    }

    @Test
    void artifactTypeFilter_mustBeAKnownType() {
        List<String> types = List.of("AVRO", "JSON", "OPENAPI");
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateArtifactTypeFilter(null, types));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateArtifactTypeFilter("AVRO", types));
        assertMessage("artifactTypeFilter: must be one of the registry's artifact types",
                () -> WebhookSubscriptionValidator.validateArtifactTypeFilter("avro", types));
        assertMessage("artifactTypeFilter: must be one of the registry's artifact types",
                () -> WebhookSubscriptionValidator.validateArtifactTypeFilter("T".repeat(33), List.of("T".repeat(33))));
    }

    @Test
    void signingSecretRef_patternAndLength() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateSigningSecretRef(null));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateSigningSecretRef("ci-key_v2.prod"));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateSigningSecretRef("k".repeat(512)));
        String rule = "signingSecretRef: must be 1 to 512 characters from A-Z, a-z, 0-9, '.', '_' and '-'";
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateSigningSecretRef("k".repeat(513)));
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateSigningSecretRef(""));
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateSigningSecretRef("key/with/slash"));
    }

    // ---- update-only fields and paging ----

    @Test
    void enabled_isRequired() {
        assertMessage("enabled: is required", () -> WebhookSubscriptionValidator.validateEnabled(null));
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateEnabled(false));
    }

    @Test
    void expectedRevisionInBody_requiredAndPositive() {
        assertEquals(3L, WebhookSubscriptionValidator.validateExpectedRevision(3L));
        assertMessage("expectedRevision: is required",
                () -> WebhookSubscriptionValidator.validateExpectedRevision((Long) null));
        assertMessage("expectedRevision: must be at least 1",
                () -> WebhookSubscriptionValidator.validateExpectedRevision(0L));
    }

    @Test
    void expectedRevisionInQuery_rangeIsCheckedBeforeNarrowing() {
        assertEquals(Long.MAX_VALUE,
                WebhookSubscriptionValidator.validateExpectedRevision(BigInteger.valueOf(Long.MAX_VALUE)));
        String rule = "expectedRevision: must be between 1 and " + Long.MAX_VALUE;
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateExpectedRevision(BigInteger.ZERO));
        assertMessage(rule, () -> WebhookSubscriptionValidator
                .validateExpectedRevision(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertMessage("expectedRevision: is required",
                () -> WebhookSubscriptionValidator.validateExpectedRevision((BigInteger) null));
    }

    @Test
    void offset_defaultsToZeroAndRejectsNegativeOrOverflow() {
        assertEquals(0, WebhookSubscriptionValidator.validateOffset(null));
        assertEquals(Integer.MAX_VALUE,
                WebhookSubscriptionValidator.validateOffset(BigInteger.valueOf(Integer.MAX_VALUE)));
        String rule = "offset: must be between 0 and " + Integer.MAX_VALUE;
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateOffset(BigInteger.valueOf(-1)));
        assertMessage(rule, () -> WebhookSubscriptionValidator
                .validateOffset(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)));
    }

    @Test
    void limit_defaultsToTwentyAndAllowsOneToHundred() {
        assertEquals(20, WebhookSubscriptionValidator.validateLimit(null));
        assertEquals(1, WebhookSubscriptionValidator.validateLimit(BigInteger.ONE));
        assertEquals(100, WebhookSubscriptionValidator.validateLimit(BigInteger.valueOf(100)));
        String rule = "limit: must be between 1 and 100";
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateLimit(BigInteger.ZERO));
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateLimit(BigInteger.valueOf(101)));
        assertMessage(rule, () -> WebhookSubscriptionValidator.validateLimit(new BigInteger("99999999999999999999")));
    }

    @Test
    void ownerId_limitedTo256() {
        assertDoesNotThrow(() -> WebhookSubscriptionValidator.validateOwnerId("o".repeat(256)));
        assertMessage("ownerId: the caller's principal name must be at most 256 characters",
                () -> WebhookSubscriptionValidator.validateOwnerId("o".repeat(257)));
    }

    // ---- messages never echo the submitted value ----

    @Test
    void messagesNeverContainTheSubmittedValue() {
        String secretUrl = "https://user:topsecret-token@hooks.example.com/x";
        WebhookSubscriptionValidationException urlError = assertThrows(WebhookSubscriptionValidationException.class,
                () -> WebhookSubscriptionValidator.validateEndpointUrl(secretUrl));
        assertFalse(urlError.getMessage().contains("topsecret-token"));
        assertFalse(urlError.getMessage().contains("hooks.example.com"));

        String group = "private-team-group\n";
        WebhookSubscriptionValidationException groupError = assertThrows(
                WebhookSubscriptionValidationException.class,
                () -> WebhookSubscriptionValidator.normalizeGroupFilter(group));
        assertFalse(groupError.getMessage().contains("private-team-group"));

        WebhookSubscriptionValidationException typeError = assertThrows(
                WebhookSubscriptionValidationException.class,
                () -> WebhookSubscriptionValidator.validateEventTypes(List.of("my-custom-event")));
        assertFalse(typeError.getMessage().contains("my-custom-event"));
    }

    private static void assertMessage(String expected, Executable executable) {
        WebhookSubscriptionValidationException ex = assertThrows(WebhookSubscriptionValidationException.class,
                executable);
        assertEquals(expected, ex.getMessage());
    }
}
