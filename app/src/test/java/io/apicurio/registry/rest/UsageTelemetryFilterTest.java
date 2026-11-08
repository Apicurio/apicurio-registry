package io.apicurio.registry.rest;

import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operation header is stored in a VARCHAR(32) column, ASCII on MySQL. A value the column cannot hold
 * would fail the whole flush batch, so the filter records it as UNKNOWN instead.
 */
class UsageTelemetryFilterTest {

    @Test
    void theOperationsSerDesSendAreKept() {
        Assertions.assertEquals("SERIALIZE", recordedOperation("SERIALIZE"));
        Assertions.assertEquals("DESERIALIZE", recordedOperation("DESERIALIZE"));
    }

    @Test
    void printableAsciiOperationsAreKept() {
        Assertions.assertEquals("schema:read", recordedOperation("schema:read"));
        Assertions.assertEquals("schema/read v2", recordedOperation("schema/read v2"));
    }

    @Test
    void surroundingSpacesAreTrimmed() {
        Assertions.assertEquals("SERIALIZE", recordedOperation("  SERIALIZE "));
        Assertions.assertEquals("UNKNOWN", recordedOperation("   "));
    }

    @Test
    void anOperationLongerThanTheColumnIsRecordedAsUnknown() {
        Assertions.assertEquals("S".repeat(32), recordedOperation("S".repeat(32)), "the column limit itself");
        Assertions.assertEquals("UNKNOWN", recordedOperation("S".repeat(33)));
        Assertions.assertEquals("UNKNOWN", recordedOperation("S".repeat(40)));
    }

    @Test
    void aNonAsciiOrControlCharacterOperationIsRecordedAsUnknown() {
        Assertions.assertEquals("UNKNOWN", recordedOperation("S\u00c9RIALIZE"));
        Assertions.assertEquals("UNKNOWN", recordedOperation("SERIAL\u2028IZE"));
        Assertions.assertEquals("UNKNOWN", recordedOperation("SERIAL\tIZE"));
    }

    @Test
    void aMissingOrEmptyOperationIsRecordedAsUnknown() {
        Assertions.assertEquals("UNKNOWN", recordedOperation(null));
        Assertions.assertEquals("UNKNOWN", recordedOperation(""));
    }

    private static String recordedOperation(String operation) {
        UsageTelemetryFilter filter = new UsageTelemetryFilter();
        filter.config = mock(UsageTelemetryConfig.class);
        when(filter.config.isEnabled()).thenReturn(true);
        filter.telemetryBuffer = mock(UsageTelemetryBuffer.class);

        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("/apis/registry/v3/ids/globalIds/42");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaderString("X-Registry-Client-Id")).thenReturn("app");
        when(request.getHeaderString("X-Registry-Operation")).thenReturn(operation);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(200);

        filter.filter(request, response);

        ArgumentCaptor<SchemaUsageEventDto> event = ArgumentCaptor.forClass(SchemaUsageEventDto.class);
        verify(filter.telemetryBuffer).addEvent(event.capture());
        Assertions.assertEquals(42L, event.getValue().getGlobalId());
        return event.getValue().getOperation();
    }
}
