package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.ValuedEnumParser;
import com.microsoft.kiota.store.BackingStoreFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.List;

/**
 * A decorator for {@link RequestAdapter} that injects OpenTelemetry trace context headers
 * (e.g. {@code traceparent}, {@code tracestate}) into outgoing HTTP requests.
 *
 * <p>When OpenTelemetry is not configured, the {@link GlobalOpenTelemetry} propagator is a no-op,
 * so this decorator is safe to always apply without any additional dependencies or configuration.</p>
 */
class OTelRequestAdapterDecorator implements RequestAdapter {

    private static final TextMapSetter<RequestInformation> OTEL_SETTER =
            (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.headers.add(key, value);
                }
            };

    private final RequestAdapter delegate;

    /**
     * Creates a new decorator wrapping the given {@link RequestAdapter}.
     *
     * @param delegate the adapter to delegate to after injecting trace context
     */
    OTelRequestAdapterDecorator(RequestAdapter delegate) {
        this.delegate = delegate;
    }

    /**
     * Injects the current OpenTelemetry trace context headers into the given request.
     *
     * @param requestInfo the request to inject headers into
     */
    private void injectTraceContext(RequestInformation requestInfo) {
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), requestInfo, OTEL_SETTER);
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> ModelType send(@Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull ParsableFactory<ModelType> factory) {
        injectTraceContext(requestInfo);
        return delegate.send(requestInfo, errorMappings, factory);
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> List<ModelType> sendCollection(
            @Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull ParsableFactory<ModelType> factory) {
        injectTraceContext(requestInfo);
        return delegate.sendCollection(requestInfo, errorMappings, factory);
    }

    @Nullable
    @Override
    public <ModelType> ModelType sendPrimitive(@Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull Class<ModelType> targetClass) {
        injectTraceContext(requestInfo);
        return delegate.sendPrimitive(requestInfo, errorMappings, targetClass);
    }

    @Nullable
    @Override
    public <ModelType> List<ModelType> sendPrimitiveCollection(
            @Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull Class<ModelType> targetClass) {
        injectTraceContext(requestInfo);
        return delegate.sendPrimitiveCollection(requestInfo, errorMappings, targetClass);
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> ModelType sendEnum(
            @Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull ValuedEnumParser<ModelType> enumParser) {
        injectTraceContext(requestInfo);
        return delegate.sendEnum(requestInfo, errorMappings, enumParser);
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> List<ModelType> sendEnumCollection(
            @Nonnull RequestInformation requestInfo,
            @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings,
            @Nonnull ValuedEnumParser<ModelType> enumParser) {
        injectTraceContext(requestInfo);
        return delegate.sendEnumCollection(requestInfo, errorMappings, enumParser);
    }

    @Override
    public void enableBackingStore(@Nullable BackingStoreFactory backingStoreFactory) {
        delegate.enableBackingStore(backingStoreFactory);
    }

    @Nonnull
    @Override
    public SerializationWriterFactory getSerializationWriterFactory() {
        return delegate.getSerializationWriterFactory();
    }

    @Override
    public void setBaseUrl(@Nonnull String baseUrl) {
        delegate.setBaseUrl(baseUrl);
    }

    @Nonnull
    @Override
    public String getBaseUrl() {
        return delegate.getBaseUrl();
    }

    @Nonnull
    @Override
    public <T> T convertToNativeRequest(@Nonnull RequestInformation requestInfo) {
        return delegate.convertToNativeRequest(requestInfo);
    }
}
