package io.apicurio.registry.resolver;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.serialization.ValuedEnumParser;
import com.microsoft.kiota.store.BackingStoreFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MockRequestAdapter implements RequestAdapter {

    private String schemaContent;
    public int timesGetContentByHashCalled;

    public MockRequestAdapter(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    @Override
    public void enableBackingStore(@Nullable BackingStoreFactory backingStoreFactory) {
    }

    @Nonnull
    @Override
    public SerializationWriterFactory getSerializationWriterFactory() {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> ModelType send(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull ParsableFactory<ModelType> factory) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> List<ModelType> sendCollection(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull ParsableFactory<ModelType> factory) {
        assertEquals("{+baseurl}/ids/contentHashes/{contentHash}/references", requestInfo.urlTemplate);
        return List.of();
    }

    @Nullable
    @Override
    public <ModelType> ModelType sendPrimitive(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull Class<ModelType> targetClass) {
        assertEquals("{+baseurl}/ids/contentHashes/{contentHash}", requestInfo.urlTemplate);
        this.timesGetContentByHashCalled++;
        return (ModelType)new ByteArrayInputStream(this.schemaContent.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    @Override
    public <ModelType> List<ModelType> sendPrimitiveCollection(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull Class<ModelType> targetClass) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> ModelType sendEnum(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull ValuedEnumParser<ModelType> enumParser) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> List<ModelType> sendEnumCollection(@Nonnull RequestInformation requestInfo, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings, @Nonnull ValuedEnumParser<ModelType> enumParser) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Override
    public void setBaseUrl(@Nonnull String baseUrl) {

    }

    @Nonnull
    @Override
    public String getBaseUrl() {
        return null;
    }

    @Nonnull
    @Override
    public <T> T convertToNativeRequest(@Nonnull RequestInformation requestInfo) {
        throw new UnsupportedOperationException("Unimplemented");
    }
}
