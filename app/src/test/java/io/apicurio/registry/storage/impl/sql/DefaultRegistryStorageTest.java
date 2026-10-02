package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class DefaultRegistryStorageTest extends AbstractRegistryStorageTest {

    private static final String SEQUENCE_GROUP_ID = DefaultRegistryStorageTest.class.getSimpleName();

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    @Test
    public void testConcurrentSequenceAllocationProducesUniqueIds() throws Exception {
        int threads = 4;
        int idsPerThread = 25;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    List<Long> ids = new ArrayList<>(idsPerThread * 2);
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(storage().nextGlobalId());
                        ids.add(storage().nextContentId());
                    }
                    return ids;
                }));
            }
            start.countDown();

            List<Long> globalIds = new ArrayList<>();
            List<Long> contentIds = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                List<Long> ids = future.get(60, TimeUnit.SECONDS);
                for (int i = 0; i < ids.size(); i += 2) {
                    globalIds.add(ids.get(i));
                    contentIds.add(ids.get(i + 1));
                }
            }

            int expected = threads * idsPerThread;
            Assertions.assertEquals(expected, globalIds.size());
            Assertions.assertEquals(expected, Set.copyOf(globalIds).size(),
                    "Global IDs handed out concurrently must be unique");
            Assertions.assertEquals(expected, Set.copyOf(contentIds).size(),
                    "Content IDs handed out concurrently must be unique");
            Assertions.assertTrue(globalIds.stream().allMatch(id -> id > 0));
            Assertions.assertTrue(contentIds.stream().allMatch(id -> id > 0));
        } finally {
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testGetVersionMetaDataByContentReturnsNewestVersion() throws Exception {
        String artifactId = "testGetVersionMetaDataByContentReturnsNewestVersion";
        ContentHandle content = ContentHandle.create(OPENAPI_CONTENT);
        ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                .contentType(ContentTypes.APPLICATION_JSON).content(content).build();

        storage().createArtifact(SEQUENCE_GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                contentWrapper, null, Collections.emptyList(), false, false, null);
        ArtifactVersionMetaDataDto second = storage().createArtifactVersion(SEQUENCE_GROUP_ID, artifactId,
                null, ArtifactType.OPENAPI, contentWrapper, null, Collections.emptyList(), false, false,
                null);
        ArtifactVersionMetaDataDto third = storage().createArtifactVersion(SEQUENCE_GROUP_ID, artifactId,
                null, ArtifactType.OPENAPI, contentWrapper, null, Collections.emptyList(), false, false,
                null);
        Assertions.assertTrue(third.getVersionOrder() > second.getVersionOrder());

        TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_JSON);

        ArtifactVersionMetaDataDto byContent = storage().getArtifactVersionMetaDataByContent(
                SEQUENCE_GROUP_ID, artifactId, false, typedContent, Collections.emptyList());
        Assertions.assertEquals(third.getVersion(), byContent.getVersion());
        Assertions.assertEquals(third.getVersionOrder(), byContent.getVersionOrder());

        ArtifactVersionMetaDataDto byCanonicalContent = storage().getArtifactVersionMetaDataByContent(
                SEQUENCE_GROUP_ID, artifactId, true, typedContent, Collections.emptyList());
        Assertions.assertEquals(third.getVersion(), byCanonicalContent.getVersion());
        Assertions.assertEquals(third.getVersionOrder(), byCanonicalContent.getVersionOrder());
    }
}
