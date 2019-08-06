package io.apicurio.registry.storage;

import io.apicurio.registry.storage.inmemory.InMemory;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class GlobalArtifactStorageTest {

    @Inject
    @InMemory
    private ArtifactStorage artifactStorage;

    @Inject
    @InMemory
    private GlobalArtifactStorage globalArtifactStorage;


    @Test
    public void createAndGetSimple() {
        ArtifactVersion value1 = artifactStorage.create().create(new ArtifactVersion("contentA"));
        ArtifactVersion value2 = globalArtifactStorage.get(value1.getGlobalId());
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(value1.getContent(), value2.getContent());
    }

    @Test
    public void createAndDeleteSimple() {
        ArtifactVersion value1 = artifactStorage.create().create(new ArtifactVersion("contentB"));
        artifactStorage.get(value1.getId().asArtifactId()).delete(value1.getId());
        ArtifactVersion value2 = globalArtifactStorage.get(value1.getGlobalId());
        Assertions.assertNull(value2);
    }

    @Test
    public void createGetDeleteConcurrent() throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        Set<ArtifactVersion> versions = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 50; i++) {
            final int j = i;
            executorService.submit(() -> {
                ArtifactVersion value1 = artifactStorage.create().create(new ArtifactVersion("contentC" + j));
                ArtifactVersion value2 = globalArtifactStorage.get(value1.getGlobalId());
                Assertions.assertEquals(value1, value2);
                Assertions.assertEquals(value1.getContent(), value2.getContent());
                artifactStorage.get(value1.getId().asArtifactId()).delete(value1.getId());
                versions.add(value1);
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        for (ArtifactVersion version : versions) {
            Assertions.assertNull(artifactStorage.get(version.getId().asArtifactId()).get(version.getId()));
            Assertions.assertNull(globalArtifactStorage.get(version.getGlobalId()));
        }
    }
}
