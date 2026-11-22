package io.apicurio.registry.rest;

import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.apicurio.registry.observability.OTelAttributes;
import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link UsageTelemetryFilter}: which requests produce the OpenTelemetry observation, how the
 * two independent switches combine, and that the legacy SQL path is untouched.
 */
class UsageTelemetryFilterTest {

    private static final String GLOBAL_ROUTE = "/apis/registry/v3/ids/globalIds/";
    private static final String CONTENT_ROUTE = "/apis/registry/v3/ids/contentIds/";

    private InMemoryMetricReader reader;
    private UsageTelemetryConfig sqlConfig;
    private UsageMetricsConfig metricsConfig;
    private UsageTelemetryBuffer buffer;
    private UsageTelemetryFilter filter;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        reader = InMemoryMetricReader.create();
        OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
                .buildAndRegisterGlobal();
        OTelMetricsProvider provider = new OTelMetricsProvider();
        provider.init();

        sqlConfig = mock(UsageTelemetryConfig.class);
        metricsConfig = mock(UsageMetricsConfig.class);
        buffer = mock(UsageTelemetryBuffer.class);
        filter = new UsageTelemetryFilter();
        filter.config = sqlConfig;
        filter.metricsConfig = metricsConfig;
        filter.telemetryBuffer = buffer;
        filter.metricsProvider = provider;
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    private void switches(boolean sql, boolean metric) {
        when(sqlConfig.isEnabled()).thenReturn(sql);
        when(metricsConfig.isEnabled()).thenReturn(metric);
    }

    private static ContainerRequestContext request(String method, String path, String clientId,
            String operation) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeaderString("X-Registry-Client-Id")).thenReturn(clientId);
        when(request.getHeaderString("X-Registry-Operation")).thenReturn(operation);
        return request;
    }

    private static ContainerResponseContext response(int status) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    private void fetch(String path, String clientId, String operation) {
        filter.filter(request("GET", path, clientId, operation), response(200));
    }

    private List<LongPointData> points() {
        return reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("apicurio.usage.schema.fetches"))
                .flatMap(m -> m.getLongSumData().getPoints().stream()).collect(Collectors.toList());
    }

    private static Map<String, String> labels(LongPointData point) {
        return Map.of(
                "client", point.getAttributes().get(OTelAttributes.ATTR_CLIENT_ID),
                "type", point.getAttributes().get(OTelAttributes.ATTR_IDENTIFIER_TYPE),
                "value", point.getAttributes().get(OTelAttributes.ATTR_IDENTIFIER_VALUE),
                "operation", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
    }

    // -- switches --

    @Test
    void otelOnlyCountsTheFetchAndNeverTouchesTheUsageBuffer() {
        switches(false, true);

        fetch(GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE");

        assertEquals(1, points().size());
        assertEquals(1L, points().get(0).getValue());
        assertEquals(Map.of("client", "payments-svc", "type", "global_id", "value", "42", "operation",
                "SERIALIZE"), labels(points().get(0)));
        verifyNoInteractions(buffer);
    }

    @Test
    void sqlOnlyEnqueuesTheLegacyEventAndRecordsNoMetric() {
        switches(true, false);

        fetch(GLOBAL_ROUTE + "42", "payments-svc", "serialize");

        ArgumentCaptor<SchemaUsageEventDto> event = ArgumentCaptor.forClass(SchemaUsageEventDto.class);
        verify(buffer).addEvent(event.capture());
        assertEquals(42L, event.getValue().getGlobalId());
        assertEquals(0L, event.getValue().getContentId());
        assertEquals("payments-svc", event.getValue().getClientId());
        assertEquals("serialize", event.getValue().getOperation(), "the legacy path keeps the raw operation");
        assertTrue(points().isEmpty());
    }

    @Test
    void bothOnRecordsTheMetricAndEnqueuesTheEvent() {
        switches(true, true);

        fetch(CONTENT_ROUTE + "9", "orders", null);

        assertEquals(1, points().size());
        assertEquals(Map.of("client", "orders", "type", "content_id", "value", "9", "operation", "UNKNOWN"),
                labels(points().get(0)));
        ArgumentCaptor<SchemaUsageEventDto> event = ArgumentCaptor.forClass(SchemaUsageEventDto.class);
        verify(buffer).addEvent(event.capture());
        assertEquals(9L, event.getValue().getContentId());
        assertEquals("UNKNOWN", event.getValue().getOperation());
    }

    @Test
    void bothOnCountsEveryFetchEvenThoughTheBufferDeduplicates() {
        switches(true, true);

        for (int i = 0; i < 5; i++) {
            fetch(GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE");
        }

        assertEquals(5L, points().get(0).getValue());
        verify(buffer, times(5)).addEvent(any());
    }

    @Test
    void bothOffDoesNothing() {
        switches(false, false);

        fetch(GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE");

        assertTrue(points().isEmpty());
        verifyNoInteractions(buffer);
    }

    // -- the OpenTelemetry route and label rules --

    @ParameterizedTest
    @ValueSource(strings = {
            "/apis/registry/v3/ids/globalIds/42/references",
            "/apis/registry/v3/ids/globalIds/",
            "/apis/registry/v3/ids/globalIds/abc",
            "/apis/registry/v3/ids/globalIds/42abc",
            "/apis/registry/v3/ids/globalIds/-1",
            "/apis/registry/v3/ids/globalIds/0",
            "/apis/registry/v3/ids/globalIds/9223372036854775808",
            "/apis/registry/v3/ids/contentIds/99999999999999999999",
            "/apis/registry/v3/ids/globalIds/0000000000000000000000000000",
            "/apis/registry/v3/ids/contentIds/42/references",
            "/apis/registry/v3/ids/contentHashes/abcdef/",
            "/apis/registry/v3/ids/globalIdsX/42",
            "/apis/registry/v3/groups/g/artifacts/a/ids/globalIds/42",
            "/apis/registry/v3/search/ids/globalIds/42",
            "/apis/registry/v2/ids/globalIds/42",
            "/apis/ccompat/v7/schemas/ids/42",
            "/prefix/apis/registry/v3/ids/globalIds/42",
            "/apis/registry/v3/groups/g/artifacts/a/versions/1/content"
    })
    void unsupportedRoutesAreNotCounted(String path) {
        switches(false, true);

        fetch(path, "payments-svc", "SERIALIZE");

        assertTrue(points().isEmpty(), "must not be counted: " + path);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/apis/registry/v3/ids/globalIds/42", "/apis/registry/v3/ids/globalIds/42/" })
    void theServerAcceptsAnOptionalTrailingSlashSoItIsCountedOnce(String path) {
        switches(false, true);

        fetch(path, "payments-svc", "SERIALIZE");

        assertEquals(1, points().size());
        assertEquals("42", labels(points().get(0)).get("value"));
    }

    static Stream<Arguments> idsAtTheBoundariesOfTheInt64Range() {
        return Stream.of(
                Arguments.of("1", "1"),
                Arguments.of("999999999999999999", "999999999999999999"),
                Arguments.of("1000000000000000000", "1000000000000000000"),
                Arguments.of("9223372036854775807", "9223372036854775807"),
                Arguments.of("0000000000000000000000000042", "42"),
                Arguments.of("0009223372036854775807", "9223372036854775807"));
    }

    @ParameterizedTest
    @MethodSource("idsAtTheBoundariesOfTheInt64Range")
    void everyPositiveInt64IdIsCountedOnBothRoutes(String id, String expectedLabel) {
        switches(false, true);

        fetch(GLOBAL_ROUTE + id, "payments-svc", "SERIALIZE");
        fetch(CONTENT_ROUTE + id, "payments-svc", "SERIALIZE");

        assertEquals(2, points().size());
        assertTrue(points().stream().allMatch(p -> p.getValue() == 1L));
        assertEquals(List.of(expectedLabel, expectedLabel),
                points().stream().map(p -> labels(p).get("value")).collect(Collectors.toList()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "POST", "HEAD", "PUT", "DELETE", "get" })
    void onlyGetIsCounted(String method) {
        switches(false, true);

        filter.filter(request(method, GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE"), response(200));

        assertTrue(points().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = { 201, 204, 301, 304, 400, 403, 404, 409, 500, 503 })
    void unsuccessfulResponsesAreNotCounted(int status) {
        switches(false, true);

        filter.filter(request("GET", GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE"), response(status));

        assertTrue(points().isEmpty());
    }

    static Stream<String> invalidClientIds() {
        return Stream.of(null, "", " ", "bad id", "client/1", "client;DROP", "clïent", "a".repeat(257),
                "client\n", "client,x=y", "{client}", "client%20x");
    }

    @ParameterizedTest
    @MethodSource("invalidClientIds")
    void missingOrInvalidClientIdsAreNotCounted(String clientId) {
        switches(false, true);

        fetch(GLOBAL_ROUTE + "42", clientId, "SERIALIZE");

        assertTrue(points().isEmpty());
    }

    @Test
    void aClientIdAtTheLengthLimitIsCounted() {
        switches(false, true);
        String clientId = "a".repeat(256);

        fetch(GLOBAL_ROUTE + "42", clientId, "SERIALIZE");

        assertEquals(clientId, labels(points().get(0)).get("client"));
    }

    @Test
    void leadingZerosDoNotCreateASecondSeriesForTheSameId() {
        switches(false, true);

        fetch(GLOBAL_ROUTE + "42", "c", "SERIALIZE");
        fetch(GLOBAL_ROUTE + "0042", "c", "SERIALIZE");

        assertEquals(1, points().size());
        assertEquals(2L, points().get(0).getValue());
        assertEquals("42", labels(points().get(0)).get("value"));
    }

    @Test
    void theLegacySqlPathStillAcceptsWhatTheMetricRejects() {
        switches(true, true);

        fetch("/apis/registry/v3/ids/globalIds/42/references", "payments-svc", "SERIALIZE");

        assertTrue(points().isEmpty(), "the reference listing is not a schema fetch");
        verify(buffer).addEvent(any());
    }

    @Test
    void aMetricFailureDoesNotBreakTheRequestOrTheSqlPath() {
        switches(true, true);
        OTelMetricsProvider failing = mock(OTelMetricsProvider.class);
        doThrow(new IllegalStateException("boom")).when(failing).recordSchemaFetch(any(), any(),
                anyLong(), any());
        filter.metricsProvider = failing;

        fetch(GLOBAL_ROUTE + "42", "payments-svc", "SERIALIZE");

        verify(failing).recordSchemaFetch("payments-svc", "global_id", 42L, "SERIALIZE");
        verify(buffer).addEvent(any());
    }

    @Test
    void anUnsupportedRouteWithMetricsOnlyNeverEnqueuesAnything() {
        switches(false, true);

        fetch("/apis/registry/v3/ids/globalIds/42/references", "payments-svc", "SERIALIZE");

        verify(buffer, never()).addEvent(any());
    }
}
