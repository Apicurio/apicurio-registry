package io.apicurio.registry.rest;

import io.apicurio.registry.metrics.MetricsConstants;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Provider
public class UsageTelemetryFilter implements ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(UsageTelemetryFilter.class);

    private static final String CLIENT_ID_HEADER = "X-Registry-Client-Id";
    private static final String OPERATION_HEADER = "X-Registry-Operation";
    private static final String DEFAULT_OPERATION = "UNKNOWN";
    private static final String GLOBAL_ID_PATH_SEGMENT = "/ids/globalIds/";
    private static final String CONTENT_ID_PATH_SEGMENT = "/ids/contentIds/";
    private static final int MAX_CLIENT_ID_LENGTH = 256;
    private static final Pattern VALID_CLIENT_ID = Pattern.compile("^[a-zA-Z0-9._-]+$");
    /** The only routes that count as a schema fetch for the OpenTelemetry metric. */
    private static final Pattern SCHEMA_FETCH_ROUTE = Pattern
            .compile("^/apis/registry/v3/ids/(globalIds|contentIds)/([+]?[0-9]+)/?$");

    @Inject
    UsageTelemetryConfig config;

    @Inject
    UsageMetricsConfig metricsConfig;

    @Inject
    UsageTelemetryBuffer telemetryBuffer;

    @Inject
    OTelMetricsProvider metricsProvider;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        boolean sqlEnabled = config.isEnabled();
        boolean metricsEnabled = metricsConfig.isEnabled();
        if (!sqlEnabled && !metricsEnabled) {
            return;
        }

        String clientId = requestContext.getHeaderString(CLIENT_ID_HEADER);
        if (clientId == null || clientId.isEmpty() || clientId.length() > MAX_CLIENT_ID_LENGTH
                || !VALID_CLIENT_ID.matcher(clientId).matches()) {
            return;
        }

        if (responseContext.getStatus() != 200) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        if (metricsEnabled) {
            recordSchemaFetchMetric(requestContext, clientId, path);
        }
        if (sqlEnabled) {
            recordSqlUsage(requestContext, clientId, path);
        }
    }

    /**
     * Counts one observed fetch on the exact supported route. Runs before the SQL deduplication and never
     * touches storage; a failure here must not affect the response or the SQL usage recording.
     */
    private void recordSchemaFetchMetric(ContainerRequestContext requestContext, String clientId, String path) {
        try {
            if (!"GET".equals(requestContext.getMethod())) {
                return;
            }
            Matcher route = SCHEMA_FETCH_ROUTE.matcher(path);
            if (!route.matches()) {
                return;
            }
            // The API takes an int64: the digits are accepted at any length (leading zeros included), and
            // anything beyond Long.MAX_VALUE or below 1 is not an identifier.
            long id;
            try {
                id = Long.parseLong(route.group(2));
            } catch (NumberFormatException e) {
                return;
            }
            if (id < 1) {
                return;
            }
            String identifierType = "globalIds".equals(route.group(1))
                    ? MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID
                    : MetricsConstants.USAGE_IDENTIFIER_TYPE_CONTENT_ID;
            metricsProvider.recordSchemaFetch(clientId, identifierType, id,
                    requestContext.getHeaderString(OPERATION_HEADER));
        } catch (RuntimeException e) {
            log.debug("Failed to record the schema fetch usage metric", e);
        }
    }

    private void recordSqlUsage(ContainerRequestContext requestContext, String clientId, String path) {
        Long globalId = extractId(path, GLOBAL_ID_PATH_SEGMENT);
        Long contentId = extractId(path, CONTENT_ID_PATH_SEGMENT);
        if (globalId == null && contentId == null) {
            return;
        }

        String operation = requestContext.getHeaderString(OPERATION_HEADER);
        if (operation == null || operation.isEmpty()) {
            operation = DEFAULT_OPERATION;
        }

        SchemaUsageEventDto event = SchemaUsageEventDto.builder()
                .globalId(globalId != null ? globalId : 0)
                .contentId(contentId != null ? contentId : 0)
                .clientId(clientId)
                .operation(operation)
                .eventTimestamp(System.currentTimeMillis())
                .build();
        telemetryBuffer.addEvent(event);
    }

    private Long extractId(String path, String segment) {
        if (path.contains(segment)) {
            return parseLongAfter(path, segment);
        }
        return null;
    }

    private Long parseLongAfter(String path, String prefix) {
        int start = path.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        start += prefix.length();
        int end = start;
        while (end < path.length() && Character.isDigit(path.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        try {
            return Long.parseLong(path.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
