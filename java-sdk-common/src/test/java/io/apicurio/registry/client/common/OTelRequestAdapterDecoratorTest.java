package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.ValuedEnumParser;
import com.microsoft.kiota.store.BackingStoreFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OTelRequestAdapterDecorator}.
 * Verifies that the decorator correctly delegates to the wrapped adapter
 * and that trace context injection does not fail when no OTel SDK is configured
 * (the default no-op propagator is used).
 */
class OTelRequestAdapterDecoratorTest {

    /**
     * Verifies that the send() method delegates to the wrapped adapter and that
     * trace context injection does not throw when no OTel SDK is active.
     */
    @Test
    void testSendDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.send(requestInfo, null, null);

        assertEquals(1, recording.sendCallCount, "send() should have been delegated");
    }

    /**
     * Verifies that sendCollection() delegates correctly.
     */
    @Test
    void testSendCollectionDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.sendCollection(requestInfo, null, null);

        assertEquals(1, recording.sendCollectionCallCount,
                "sendCollection() should have been delegated");
    }

    /**
     * Verifies that sendPrimitive() delegates correctly.
     */
    @Test
    void testSendPrimitiveDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.sendPrimitive(requestInfo, null, String.class);

        assertEquals(1, recording.sendPrimitiveCallCount,
                "sendPrimitive() should have been delegated");
    }

    /**
     * Verifies that sendPrimitiveCollection() delegates correctly.
     */
    @Test
    void testSendPrimitiveCollectionDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.sendPrimitiveCollection(requestInfo, null, String.class);

        assertEquals(1, recording.sendPrimitiveCollectionCallCount,
                "sendPrimitiveCollection() should have been delegated");
    }

    /**
     * Verifies that sendEnum() delegates correctly.
     */
    @Test
    void testSendEnumDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.sendEnum(requestInfo, null, null);

        assertEquals(1, recording.sendEnumCallCount,
                "sendEnum() should have been delegated");
    }

    /**
     * Verifies that sendEnumCollection() delegates correctly.
     */
    @Test
    void testSendEnumCollectionDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.sendEnumCollection(requestInfo, null, null);

        assertEquals(1, recording.sendEnumCollectionCallCount,
                "sendEnumCollection() should have been delegated");
    }

    /**
     * Verifies that getBaseUrl() delegates correctly.
     */
    @Test
    void testGetBaseUrlDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        recording.baseUrl = "http://example.com";
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        assertEquals("http://example.com", decorator.getBaseUrl());
    }

    /**
     * Verifies that setBaseUrl() delegates correctly.
     */
    @Test
    void testSetBaseUrlDelegatesToWrappedAdapter() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        decorator.setBaseUrl("http://new-url.com");

        assertEquals("http://new-url.com", recording.baseUrl);
    }

    /**
     * Verifies that when no OTel SDK is configured (no-op propagator),
     * no trace headers are added to the request.
     */
    @Test
    void testNoTraceHeadersWhenNoOTelConfigured() {
        RecordingRequestAdapter recording = new RecordingRequestAdapter();
        OTelRequestAdapterDecorator decorator = new OTelRequestAdapterDecorator(recording);

        RequestInformation requestInfo = new RequestInformation();
        decorator.send(requestInfo, null, null);

        // With no OTel SDK configured, the no-op propagator should not inject any headers
        Set<String> traceparentValues = requestInfo.headers.get("traceparent");
        assertNull(traceparentValues,
                "No traceparent header should be present when OTel is not configured");
    }

    /**
     * Verifies that the adapter created by the factory is wrapped with the OTel decorator
     * when OpenTelemetry is explicitly enabled via options.
     */
    @Test
    void testFactoryWrapsAdapterWithOTelDecoratorWhenEnabled() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .enableOpenTelemetry();

        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(
                options, Version.V3);

        assertNotNull(adapter, "Adapter should be created");
        assertTrue(adapter instanceof OTelRequestAdapterDecorator,
                "Adapter should be wrapped with OTelRequestAdapterDecorator when OTel is enabled");
    }

    /**
     * A minimal RequestAdapter implementation that records method calls for verification.
     */
    private static class RecordingRequestAdapter implements RequestAdapter {
        int sendCallCount = 0;
        int sendCollectionCallCount = 0;
        int sendPrimitiveCallCount = 0;
        int sendPrimitiveCollectionCallCount = 0;
        int sendEnumCallCount = 0;
        int sendEnumCollectionCallCount = 0;
        String baseUrl = "";

        @Override
        public void enableBackingStore(@Nullable BackingStoreFactory backingStoreFactory) {
        }

        @Nonnull
        @Override
        public SerializationWriterFactory getSerializationWriterFactory() {
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Parsable> ModelType send(@Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ParsableFactory<ModelType> factory) {
            sendCallCount++;
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Parsable> List<ModelType> sendCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ParsableFactory<ModelType> factory) {
            sendCollectionCallCount++;
            return null;
        }

        @Nullable
        @Override
        public <ModelType> ModelType sendPrimitive(@Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull Class<ModelType> targetClass) {
            sendPrimitiveCallCount++;
            return null;
        }

        @Nullable
        @Override
        public <ModelType> List<ModelType> sendPrimitiveCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull Class<ModelType> targetClass) {
            sendPrimitiveCollectionCallCount++;
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Enum<ModelType>> ModelType sendEnum(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ValuedEnumParser<ModelType> enumParser) {
            sendEnumCallCount++;
            return null;
        }

        @Nullable
        @Override
        public <ModelType extends Enum<ModelType>> List<ModelType> sendEnumCollection(
                @Nonnull RequestInformation requestInfo,
                @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
                @Nonnull ValuedEnumParser<ModelType> enumParser) {
            sendEnumCollectionCallCount++;
            return null;
        }

        @Override
        public void setBaseUrl(@Nonnull String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Nonnull
        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Nonnull
        @Override
        public <T> T convertToNativeRequest(@Nonnull RequestInformation requestInfo) {
            return null;
        }
    }
}
