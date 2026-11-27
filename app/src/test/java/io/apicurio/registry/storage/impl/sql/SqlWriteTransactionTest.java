package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction behaviour of SQL storage writes (#11412): savepoints, single-transaction creates and
 * concurrent creates. Runs on H2 here and on the other databases through the dialect subclasses.
 */
@QuarkusTest
public class SqlWriteTransactionTest {

    protected static final int THREADS = 8;

    @Inject
    @Current
    protected RegistryStorage storage;

    @Inject
    protected HandleFactory handles;

    protected static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected static ContentWrapperDto avro(String recordName) {
        String schema = "{\"type\":\"record\",\"name\":\"" + recordName.replaceAll("[^A-Za-z0-9]", "")
                + "\",\"fields\":[{\"name\":\"f\",\"type\":\"string\"}]}";
        return ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(schema)).references(Collections.emptyList()).build();
    }

    protected Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> create(String groupId, String artifactId,
            ContentWrapperDto content, boolean dryRun) {
        return storage.createArtifact(groupId, artifactId, ArtifactType.AVRO, null, null, content, null,
                Collections.emptyList(), false, dryRun, "owner");
    }

    protected long countRows(String sql, String param) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sql).bind(0, param).mapTo(Long.class).one();
        });
    }

    protected static GroupMetaDataDto group(String groupId) {
        long now = System.currentTimeMillis();
        return GroupMetaDataDto.builder().groupId(groupId).owner("owner").createdOn(now).modifiedOn(now)
                .build();
    }

    @Test
    void handledFailureUnderSavepointDoesNotRollBackTheTransaction() {
        String existing = uniqueId("sp-existing");
        String created = uniqueId("sp-created");
        storage.createGroup(group(existing));

        handles.withHandleNoException(handle -> {
            GroupAlreadyExistsException failure = assertThrows(GroupAlreadyExistsException.class,
                    () -> handle.inSavepoint(h -> {
                        storage.createGroup(group(existing));
                        return null;
                    }));
            assertEquals(existing, failure.getGroupId());
            // The transaction is still usable and still commits.
            storage.createGroup(group(created));
            return null;
        });

        assertTrue(storage.isGroupExists(created));
    }

    @Test
    void savepointRollsBackOnlyTheWorkInsideIt() {
        String kept = uniqueId("sp-kept");
        String undone = uniqueId("sp-undone");

        handles.withHandleNoException(handle -> {
            storage.createGroup(group(kept));
            assertThrows(IllegalStateException.class, () -> handle.inSavepoint(h -> {
                storage.createGroup(group(undone));
                throw new IllegalStateException("fail after the insert");
            }));
            return null;
        });

        assertTrue(storage.isGroupExists(kept));
        assertFalse(storage.isGroupExists(undone));
    }

    @Test
    void dryRunCreateLeavesNoGroupContentOrArtifact() {
        String groupId = uniqueId("dry-run");
        String artifactId = uniqueId("a");
        ContentWrapperDto content = avro(artifactId);
        long contentRowsBefore = countRows("SELECT COUNT(*) FROM content WHERE contentType = ?",
                ContentTypes.APPLICATION_JSON);

        Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> result = create(groupId, artifactId, content,
                true);

        assertEquals(artifactId, result.getLeft().getArtifactId());
        assertEquals("1", result.getRight().getVersion());
        assertFalse(storage.isGroupExists(groupId));
        assertFalse(storage.isArtifactExists(groupId, artifactId));
        assertEquals(contentRowsBefore, countRows("SELECT COUNT(*) FROM content WHERE contentType = ?",
                ContentTypes.APPLICATION_JSON));
    }

    @Test
    void createInNewGroupCreatesGroupAndArtifact() {
        // Automatic group creation is enabled by default, so the group is created with the artifact.
        String groupId = uniqueId("auto-group");
        String artifactId = uniqueId("a");

        create(groupId, artifactId, avro(artifactId), false);

        assertTrue(storage.isGroupExists(groupId));
        assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
    }

    @Test
    void concurrentCreatesOfTheSameArtifactHaveOneWinner() throws Exception {
        String groupId = uniqueId("race-same");
        String artifactId = uniqueId("a");
        ContentWrapperDto content = avro(artifactId);

        List<Throwable> outcomes = runConcurrently(THREADS, i -> {
            create(groupId, artifactId, content, false);
            return null;
        });

        long winners = outcomes.stream().filter(o -> o == null).count();
        long alreadyExists = outcomes.stream().filter(o -> o instanceof ArtifactAlreadyExistsException).count();
        assertEquals(1, winners, "outcomes: " + outcomes);
        assertEquals(THREADS - 1, alreadyExists, "outcomes: " + outcomes);
        assertTrue(storage.isGroupExists(groupId));
        assertEquals(1L, storage.countArtifactVersions(groupId, artifactId));
    }

    @Test
    void concurrentCreatesInANewGroupWithTheSameContentAllSucceed() throws Exception {
        // Every create races to insert the same new group and the same new content row.
        String groupId = uniqueId("race-group");
        ContentWrapperDto content = avro(groupId);

        List<Throwable> outcomes = runConcurrently(THREADS, i -> {
            create(groupId, "a" + i, content, false);
            return null;
        });

        assertEquals(Collections.nCopies(THREADS, null), outcomes);
        for (int i = 0; i < THREADS; i++) {
            assertTrue(storage.isArtifactExists(groupId, "a" + i));
        }
        List<Long> contentIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            contentIds.add(storage.getArtifactVersionMetaData(groupId, "a" + i, "1").getContentId());
        }
        assertEquals(1L, contentIds.stream().distinct().count(), "content ids: " + contentIds);
    }

    @FunctionalInterface
    protected interface IndexedTask {
        Void run(int index) throws Exception;
    }

    /**
     * Runs the task on that many threads, released together, and returns each thread's failure (null on
     * success), in thread order.
     */
    protected static List<Throwable> runConcurrently(int threads, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Void> callable = () -> {
                    // Storage writes read the caller's identity, which is request scoped.
                    ManagedContext requestContext = Arc.container().requestContext();
                    requestContext.activate();
                    try {
                        ready.countDown();
                        go.await();
                        return task.run(index);
                    } finally {
                        requestContext.terminate();
                    }
                };
                futures.add(executor.submit(callable));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    outcomes.add(null);
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
