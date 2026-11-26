package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of RegistryClientFacade for testing schema caching behavior.
 * Tracks the number of times each method is called to verify caching is working.
 */
public class MockRegistryClientFacade implements RegistryClientFacade {

    private final String schemaContent;
    private final AtomicInteger createSchemaCallCount = new AtomicInteger(0);
    /** One entry per createSchema call: "group/artifactId" -> the references passed. */
    private final Map<String, Set<RegistryArtifactReference>> created = new LinkedHashMap<>();
    private final List<String> createdVersions = new ArrayList<>();
    private final AtomicInteger searchVersionsCallCount = new AtomicInteger(0);
    private final AtomicInteger getSchemaByGlobalIdCallCount = new AtomicInteger(0);
    private final AtomicInteger getSchemaByGAVCallCount = new AtomicInteger(0);
    private final AtomicInteger getReferencesByGAVCallCount = new AtomicInteger(0);

    /** Every dereference flag requested, in order, e.g. "globalId:true", "contentId:false". */
    private final List<String> dereferenceRequests = new ArrayList<>();
    private boolean supportsDereferencedContentLookups = true;
    private final AtomicInteger getReferencesCallCount = new AtomicInteger(0);

    /** Optional per-GAV reference responses for testing nested references. */
    private Map<String, List<RegistryArtifactReference>> referencesByGAV = new HashMap<>();

    public MockRegistryClientFacade(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    /**
     * Configures the mock to return the given references when queried for a specific GAV.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version
     * @param references the references to return
     */
    public void addReferencesByGAV(String groupId, String artifactId, String version,
                                    List<RegistryArtifactReference> references) {
        referencesByGAV.put(gavKey(groupId, artifactId, version), references);
    }

    private static String gavKey(String groupId, String artifactId, String version) {
        return groupId + "/" + artifactId + "/" + version;
    }

    public int getCreateSchemaCallCount() {
        return createSchemaCallCount.get();
    }

    /**
     * @return the recorded createSchema calls, in call order
     */
    public Map<String, Set<RegistryArtifactReference>> getCreated() {
        return created;
    }

    /** The requested version of every createSchema call, in order. */
    public List<String> getCreatedVersions() {
        return createdVersions;
    }

    public int getSearchVersionsCallCount() {
        return searchVersionsCallCount.get();
    }

    public int getGetSchemaByGlobalIdCallCount() {
        return getSchemaByGlobalIdCallCount.get();
    }

    public int getGetSchemaByGAVCallCount() {
        return getSchemaByGAVCallCount.get();
    }

    public int getGetReferencesByGAVCallCount() {
        return getReferencesByGAVCallCount.get();
    }

    public List<String> getDereferenceRequests() {
        return dereferenceRequests;
    }

    public void setSupportsDereferencedContentLookups(boolean supports) {
        this.supportsDereferencedContentLookups = supports;
    }

    public int getGetReferencesCallCount() {
        return getReferencesCallCount.get();
    }

    @Override
    public boolean supportsDereferencedContentLookups() {
        return supportsDereferencedContentLookups;
    }

    @Override
    public String getSchemaByContentId(Long contentId, boolean dereferenced) {
        dereferenceRequests.add("contentId:" + dereferenced);
        return schemaContent;
    }

    @Override
    public String getSchemaByContentHash(String contentHash, boolean dereferenced) {
        dereferenceRequests.add("contentHash:" + dereferenced);
        return schemaContent;
    }

    @Override
    public String getSchemaByContentId(Long contentId) {
        dereferenceRequests.add("contentId:false");
        return schemaContent;
    }

    @Override
    public String getSchemaByGlobalId(long globalId, boolean dereferenced) {
        getSchemaByGlobalIdCallCount.incrementAndGet();
        dereferenceRequests.add("globalId:" + dereferenced);
        return schemaContent;
    }

    @Override
    public String getSchemaByGAV(String groupId, String artifactId, String version) {
        getSchemaByGAVCallCount.incrementAndGet();
        return schemaContent;
    }

    @Override
    public String getSchemaByContentHash(String contentHash) {
        dereferenceRequests.add("contentHash:false");
        return schemaContent;
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentId(long contentId) {
        getReferencesCallCount.incrementAndGet();
        return List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGlobalId(long globalId) {
        getReferencesCallCount.incrementAndGet();
        return List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version) {
        getReferencesByGAVCallCount.incrementAndGet();
        List<RegistryArtifactReference> refs = referencesByGAV.get(gavKey(groupId, artifactId, version));
        return refs != null ? refs : List.of();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentHash(String contentHash) {
        getReferencesCallCount.incrementAndGet();
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
        created.put(groupId + "/" + artifactId, references);
        createdVersions.add(version);
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
