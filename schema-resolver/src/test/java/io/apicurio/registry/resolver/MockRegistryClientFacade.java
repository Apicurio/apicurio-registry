package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of RegistryClientFacade for testing schema caching behavior.
 * Tracks the number of times each method is called to verify caching is working.
 */
public class MockRegistryClientFacade implements RegistryClientFacade {

    private final String schemaContent;
    private final AtomicInteger createSchemaCallCount = new AtomicInteger(0);
    private final AtomicInteger searchVersionsCallCount = new AtomicInteger(0);
    private final AtomicInteger getSchemaByGlobalIdCallCount = new AtomicInteger(0);

    public MockRegistryClientFacade(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    public int getCreateSchemaCallCount() {
        return createSchemaCallCount.get();
    }

    public int getSearchVersionsCallCount() {
        return searchVersionsCallCount.get();
    }

    public int getGetSchemaByGlobalIdCallCount() {
        return getSchemaByGlobalIdCallCount.get();
    }

    @Override
    public String getSchemaByContentId(Long contentId) {
        return schemaContent;
    }

    @Override
    public String getSchemaByGlobalId(long globalId, boolean dereferenced) {
        getSchemaByGlobalIdCallCount.incrementAndGet();
        return schemaContent;
    }

    @Override
    public String getSchemaByGAV(String groupId, String artifactId, String version) {
        return schemaContent;
    }

    @Override
    public String getSchemaByContentHash(String contentHash) {
        return schemaContent;
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentId(long contentId) {
        return List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGlobalId(long globalId) {
        return List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version) {
        return List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentHash(String contentHash) {
        return List.of();
    }

    @Override
    public List<RegistryVersionCoordinates> searchVersionsByContent(String schemaString, String artifactType,
            ArtifactReference reference, boolean canonical) {
        searchVersionsCallCount.incrementAndGet();
        return List.of(RegistryVersionCoordinates.create(1L, 1L, "default", "test-artifact", "1"));
    }

    @Override
    public RegistryVersionCoordinates createSchema(String artifactType, String groupId, String artifactId,
            String version, String autoCreateBehavior, boolean canonical, String schemaString,
            Set<RegistryArtifactReference> references) {
        createSchemaCallCount.incrementAndGet();
        return RegistryVersionCoordinates.create(1L, 1L, groupId, artifactId, "1");
    }

    @Override
    public RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version) {
        return RegistryVersionCoordinates.create(1L, 1L, groupId, artifactId, version != null ? version : "1");
    }

    @Override
    public Object getClient() {
        return null;
    }
}
