package io.apicurio.registry.rest;

import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.regex.Pattern;

@Provider
public class UsageTelemetryFilter implements ContainerResponseFilter {

    private static final String CLIENT_ID_HEADER = "X-Registry-Client-Id";
    private static final String OPERATION_HEADER = "X-Registry-Operation";
    private static final String DEFAULT_OPERATION = "UNKNOWN";
    private static final String GLOBAL_ID_PATH_SEGMENT = "/ids/globalIds/";
    private static final String CONTENT_ID_PATH_SEGMENT = "/ids/contentIds/";
    private static final int MAX_CLIENT_ID_LENGTH = 256;
    private static final Pattern VALID_CLIENT_ID = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Inject
    UsageTelemetryConfig config;

    @Inject
    UsageTelemetryBuffer telemetryBuffer;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!config.isEnabled()) {
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
