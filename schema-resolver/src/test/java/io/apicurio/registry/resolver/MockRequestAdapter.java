package io.apicurio.registry.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import com.microsoft.kiota.serialization.SerializationWriterFactory;
import com.microsoft.kiota.store.BackingStoreFactory;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public class MockRequestAdapter implements RequestAdapter {

    private String schemaContent;
    public int timesGetContentByHashCalled;

    public MockRequestAdapter(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    @Override
    public void enableBackingStore(@Nullable BackingStoreFactory backingStoreFactory) {
    }

    @NotNull
    @Override
    public SerializationWriterFactory getSerializationWriterFactory() {
        return null;
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> CompletableFuture<ModelType> sendAsync(@NotNull RequestInformation requestInfo, @NotNull ParsableFactory<ModelType> factory, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Parsable> CompletableFuture<List<ModelType>> sendCollectionAsync(@NotNull RequestInformation requestInfo, @NotNull ParsableFactory<ModelType> factory, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        assertEquals("{+baseurl}/ids/contentHashes/{contentHash}/references", requestInfo.urlTemplate);
        return CompletableFuture.completedFuture(List.of());
    }

    @Nullable
    @Override
    public <ModelType> CompletableFuture<ModelType> sendPrimitiveAsync(@NotNull RequestInformation requestInfo, @NotNull Class<ModelType> targetClass, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        assertEquals("{+baseurl}/ids/contentHashes/{contentHash}", requestInfo.urlTemplate);
        this.timesGetContentByHashCalled++;
        return CompletableFuture.completedFuture((ModelType)new ByteArrayInputStream(this.schemaContent.getBytes(StandardCharsets.UTF_8)));
    }

    @Nullable
    @Override
    public <ModelType> CompletableFuture<List<ModelType>> sendPrimitiveCollectionAsync(@NotNull RequestInformation requestInfo, @NotNull Class<ModelType> targetClass, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> CompletableFuture<ModelType> sendEnumAsync(@NotNull RequestInformation requestInfo, @NotNull Class<ModelType> targetClass, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Nullable
    @Override
    public <ModelType extends Enum<ModelType>> CompletableFuture<List<ModelType>> sendEnumCollectionAsync(@NotNull RequestInformation requestInfo, @NotNull Class<ModelType> targetClass, @Nullable HashMap<String, ParsableFactory<? extends Parsable>> errorMappings) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Override
    public void setBaseUrl(@NotNull String baseUrl) {
    }

    @NotNull
    @Override
    public String getBaseUrl() {
        return null;
    }

    @NotNull
    @Override
    public <T> CompletableFuture<T> convertToNativeRequestAsync(@NotNull RequestInformation requestInfo) {
        throw new UnsupportedOperationException("Unimplemented");
    }

}
