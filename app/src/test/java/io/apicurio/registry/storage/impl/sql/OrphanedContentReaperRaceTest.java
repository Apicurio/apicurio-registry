package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.repositories.SqlContentRepository;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;

/**
 * Reproduces the window between the content insert and the version insert (#9777, #9571): right after
 * the content row is inserted, a second thread runs the orphaned content cleanup, exactly as the
 * scheduled {@code OrphanedContentReaper} would. Every write path that stores content together with a
 * version must still succeed and keep its content, and a dry run or a failed create must leave no
 * content row behind.
 */
@QuarkusTest
public class OrphanedContentReaperRaceTest {

    private static final String GROUP_ID = "OrphanedContentReaperRaceTest";
    private static final long OTHER_THREAD_TIMEOUT_SECONDS = 5;
    private static final long REAPER_GRACE_SECONDS = 3;

    @Inject
    SqlRegistryStorage storage;

    private AbstractSqlRegistryStorage sql;
    private SqlContentRepository originalContentRepository;
    private ExecutorService otherThread;
    private final List<Future<?>> reaperRuns = new ArrayList<>();
    private final List<Boolean> cleanupFinishedWithinGrace = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sql = (AbstractSqlRegistryStorage) ClientProxy.unwrap(storage);
        originalContentRepository = sql.contentRepository;
        otherThread = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        try {
            otherThread.shutdownNow();
            Assertions.assertTrue(otherThread.awaitTermination(OTHER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the other thread must have finished");
        } finally {
            sql.contentRepository = originalContentRepository;
        }
    }

    private static String openApi(String title) {
        return "{\"openapi\": \"3.0.2\", \"info\": {\"title\": \"" + title + "\", \"version\": \"1.0.0\"}}";
    }

    private static ContentWrapperDto wrap(String json) {
        return wrap(json, Collections.emptyList());
    }

    private static ContentWrapperDto wrap(String json, List<ArtifactReferenceDto> references) {
        return ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(json)).references(references).build();
    }

    /** Submits the action to the other thread, with a request context, so it runs in its own transaction. */
    private Future<?> submitOnOtherThread(Runnable action) {
        return otherThread.submit(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                action.run();
            } finally {
                requestContext.terminate();
            }
        });
    }

    /** Runs the action on another thread, in its own transaction, and waits for it to finish. */
    private void runOnOtherThread(Runnable action) throws Exception {
        submitOnOtherThread(action).get(OTHER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** MySQL and SQL Server make a delete wait for the row lock of an uncommitted insert; the others skip it. */
    private boolean cleanupBlocksOnUncommittedRows() {
        String databaseType = sql.sqlStatements.dbType();
        return databaseType.equals("mysql") || databaseType.equals("mssql");
    }

    private Optional<Long> contentIdOf(String json) {
        String contentHash = originalContentRepository
                .hashContent(ArtifactType.OPENAPI, wrap(json), false, false).contentHash();
        return sql.contentIdFromHash(contentHash);
    }

    /**
     * From now on, every content insert is followed by one run of the orphaned content cleanup on another
     * thread, before the insert call returns to the caller. PostgreSQL and H2 let the cleanup finish at
     * once. MySQL and SQL Server make it wait for the row lock of the uncommitted insert, so the wait here
     * is bounded and the run is joined by {@link #finishReaperRuns()} after the write has committed.
     */
    private void runReaperAfterEveryContentInsert() {
        SqlContentRepository spy = Mockito.spy(originalContentRepository);
        Mockito.doAnswer(invocation -> {
            Object contentId = invocation.callRealMethod();
            Future<?> run = submitOnOtherThread(sql::deleteAllOrphanedContent);
            reaperRuns.add(run);
            try {
                run.get(REAPER_GRACE_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException ignored) {
                // A blocked run is joined after the write commits, a failed one is checked there too.
            }
            cleanupFinishedWithinGrace.add(run.isDone());
            return contentId;
        }).when(spy).ensureContentAndGetIdRaw(any(Handle.class), any());
        sql.contentRepository = spy;
    }

    /**
     * Waits for every cleanup run started by {@link #runReaperAfterEveryContentInsert()} and checks that
     * exactly one ran. A run that was blocked behind the write may fail once it proceeds, in one of two ways
     * the database uses to protect the write: it refuses to delete a row that a version now references
     * (integrity constraint violation), or it picks the cleanup as the victim of a deadlock with the write
     * (MySQL, SQL Server), or it gives up waiting for the row lock the write holds (H2). Those are the
     * only failures accepted, and only when the cleanup is expected to wait for a lock (see
     * {@link #finishReaperRuns(boolean)}); the tests assert on the outcome of the write.
     */
    private void finishReaperRuns() throws Exception {
        finishReaperRuns(!cleanupBlocksOnUncommittedRows());
    }

    /**
     * @param cleanupMustHaveFinishedFirst true when the cleanup is expected to complete without error while
     *            the write is still open, which proves it really ran inside the window; false when it waits
     *            for a lock and may then be refused
     */
    private void finishReaperRuns(boolean cleanupMustHaveFinishedFirst) throws Exception {
        Assertions.assertEquals(1, reaperRuns.size(), "the cleanup must have run once per content insert");
        if (cleanupMustHaveFinishedFirst) {
            Assertions.assertEquals(List.of(true), cleanupFinishedWithinGrace,
                    "the cleanup must have run before the write continued, or the test proves nothing");
        }
        for (Future<?> run : reaperRuns) {
            try {
                run.get(OTHER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (cleanupMustHaveFinishedFirst || !isRefusedInFavorOfTheWrite(e)) {
                    throw e;
                }
            }
        }
    }

    /**
     * SQLState class 23 is an integrity constraint violation, 40001 is a deadlock victim (a serialization
     * failure) and HYT00 is the H2 lock timeout.
     */
    private static boolean isRefusedInFavorOfTheWrite(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null
                    && (sqlException.getSQLState().startsWith("23")
                            || sqlException.getSQLState().equals("40001")
                            || sqlException.getSQLState().equals("HYT00"))) {
                return true;
            }
        }
        return false;
    }

    private static String newArtifactId() {
        return "reaper-race-" + UUID.randomUUID();
    }

    private String createArtifactWithFirstVersion(String firstContent) {
        String artifactId = newArtifactId();
        sql.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null, wrap(firstContent), null,
                Collections.emptyList(), false, false, null);
        return artifactId;
    }

    @Test
    public void testCreateArtifactVersion() throws Exception {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String secondContent = openApi("second-" + UUID.randomUUID());
        runReaperAfterEveryContentInsert();

        ArtifactVersionMetaDataDto created = sql.createArtifactVersion(GROUP_ID, artifactId, "2",
                ArtifactType.OPENAPI, wrap(secondContent), null, Collections.emptyList(), false, false, null);
        finishReaperRuns();

        Assertions.assertEquals("2", created.getVersion());
        Assertions.assertEquals(2, created.getVersionOrder());
        Assertions.assertEquals(secondContent,
                sql.getContentById(created.getContentId()).getContent().content());
    }

    @Test
    public void testCreateArtifactWithFirstVersion() throws Exception {
        String artifactId = newArtifactId();
        String content = openApi("only-" + UUID.randomUUID());
        runReaperAfterEveryContentInsert();

        ArtifactVersionMetaDataDto created = sql.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, "1", wrap(content), null, Collections.emptyList(), false, false, null).getValue();
        finishReaperRuns();

        Assertions.assertEquals("1", created.getVersion());
        Assertions.assertEquals(1, created.getVersionOrder());
        Assertions.assertEquals(content, sql.getContentById(created.getContentId()).getContent().content());
    }

    @Test
    public void testCreateArtifactVersionIfLatest() throws Exception {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String secondContent = openApi("second-" + UUID.randomUUID());
        runReaperAfterEveryContentInsert();

        ArtifactVersionMetaDataDto created = sql.createArtifactVersionIfLatest(GROUP_ID, artifactId, "2",
                ArtifactType.OPENAPI, wrap(secondContent), null, Collections.emptyList(), false, null, 1,
                null);
        finishReaperRuns();

        Assertions.assertEquals("2", created.getVersion());
        Assertions.assertEquals(2, created.getVersionOrder());
        Assertions.assertEquals(secondContent,
                sql.getContentById(created.getContentId()).getContent().content());
    }

    @Test
    public void testUpdateArtifactVersionContent() throws Exception {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String replacement = openApi("replacement-" + UUID.randomUUID());
        runReaperAfterEveryContentInsert();

        sql.updateArtifactVersionContent(GROUP_ID, artifactId, "1", ArtifactType.OPENAPI, wrap(replacement));
        finishReaperRuns();

        Assertions.assertEquals(replacement,
                sql.getArtifactVersionContent(GROUP_ID, artifactId, "1").getContent().content());
    }

    /**
     * The content already exists as an orphan: its only version was deleted and the cleanup has not run
     * yet. The new write reuses that row, so the row has to stay locked until the version references it.
     */
    @Test
    public void testReusedOrphanedContentSurvivesTheCleanup() throws Exception {
        String reusedContent = openApi("reused-" + UUID.randomUUID());
        String donorArtifactId = createArtifactWithFirstVersion(reusedContent);
        sql.deleteArtifact(GROUP_ID, donorArtifactId);
        Optional<Long> orphanedContentId = contentIdOf(reusedContent);
        Assertions.assertTrue(orphanedContentId.isPresent(), "the content must exist as an orphan");
        String artifactId = newArtifactId();
        runReaperAfterEveryContentInsert();

        ArtifactVersionMetaDataDto created = sql.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI,
                null, "1", wrap(reusedContent), null, Collections.emptyList(), false, false, null).getValue();
        finishReaperRuns(false);

        Assertions.assertEquals(orphanedContentId.get(), created.getContentId());
        Assertions.assertEquals(reusedContent,
                sql.getContentById(created.getContentId()).getContent().content());
    }

    @Test
    public void testPublishDraftVersion() throws Exception {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String draftContent = openApi("draft-" + UUID.randomUUID());
        sql.createArtifactVersion(GROUP_ID, artifactId, "2", ArtifactType.OPENAPI, wrap(draftContent), null,
                Collections.emptyList(), true, false, null);
        runReaperAfterEveryContentInsert();

        sql.updateArtifactVersionState(GROUP_ID, artifactId, "2", VersionState.ENABLED, false);
        finishReaperRuns();

        ArtifactVersionMetaDataDto published = sql.getArtifactVersionMetaData(GROUP_ID, artifactId, "2");
        Assertions.assertEquals(VersionState.ENABLED, published.getState());
        Assertions.assertEquals(draftContent,
                sql.getContentById(published.getContentId()).getContent().content());
        Assertions.assertFalse(sql.getContentById(published.getContentId()).getContentHash().startsWith("draft:"),
                "the published version must point at the content stored with its real hash");
    }

    /** The references of an orphaned content row must survive when a write reuses the row. */
    @Test
    public void testReusedOrphanedContentKeepsItsReferences() throws Exception {
        String targetArtifactId = createArtifactWithFirstVersion(openApi("target-" + UUID.randomUUID()));
        List<ArtifactReferenceDto> references = List.of(ArtifactReferenceDto.builder().groupId(GROUP_ID)
                .artifactId(targetArtifactId).version("1").name("target.json").build());
        String reusedContent = openApi("reused-with-refs-" + UUID.randomUUID());
        String donorArtifactId = newArtifactId();
        sql.createArtifact(GROUP_ID, donorArtifactId, ArtifactType.OPENAPI, null, "1",
                wrap(reusedContent, references), null, Collections.emptyList(), false, false, null);
        sql.deleteArtifact(GROUP_ID, donorArtifactId);
        runReaperAfterEveryContentInsert();

        ArtifactVersionMetaDataDto created = sql.createArtifact(GROUP_ID, newArtifactId(),
                ArtifactType.OPENAPI, null, "1", wrap(reusedContent, references), null,
                Collections.emptyList(), false, false, null).getValue();
        finishReaperRuns(false);

        Assertions.assertEquals(List.of(created.getContentId()),
                sql.getContentIdsReferencingArtifactVersion(GROUP_ID, targetArtifactId, "1"));
    }

    /** The cleanup no longer deletes references in a statement of its own, so the cascade must do it. */
    @Test
    public void testCleanupRemovesTheReferencesOfDeletedOrphanedContent() {
        String targetArtifactId = createArtifactWithFirstVersion(openApi("target-" + UUID.randomUUID()));
        List<ArtifactReferenceDto> references = List.of(ArtifactReferenceDto.builder().groupId(GROUP_ID)
                .artifactId(targetArtifactId).version("1").name("target.json").build());
        String orphanedContent = openApi("orphaned-with-refs-" + UUID.randomUUID());
        String donorArtifactId = newArtifactId();
        sql.createArtifact(GROUP_ID, donorArtifactId, ArtifactType.OPENAPI, null, "1",
                wrap(orphanedContent, references), null, Collections.emptyList(), false, false, null);
        Assertions.assertEquals(1, sql.getContentIdsReferencingArtifactVersion(GROUP_ID, targetArtifactId, "1")
                .size());
        sql.deleteArtifact(GROUP_ID, donorArtifactId);

        sql.deleteAllOrphanedContent();

        Assertions.assertEquals(Optional.empty(), contentIdOf(orphanedContent));
        Assertions.assertEquals(List.of(),
                sql.getContentIdsReferencingArtifactVersion(GROUP_ID, targetArtifactId, "1"));
    }

    @Test
    public void testDryRunLeavesNoContentRow() {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String dryRunContent = openApi("dry-run-" + UUID.randomUUID());

        sql.createArtifactVersion(GROUP_ID, artifactId, "2", ArtifactType.OPENAPI, wrap(dryRunContent), null,
                Collections.emptyList(), false, true, null);

        Assertions.assertEquals(Optional.empty(), contentIdOf(dryRunContent));
    }

    @Test
    public void testRejectedVersionLeavesNoContentRow() {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        sql.createArtifactVersion(GROUP_ID, artifactId, "2", ArtifactType.OPENAPI,
                wrap(openApi("second-" + UUID.randomUUID())), null, Collections.emptyList(), false, false,
                null);
        String rejectedContent = openApi("rejected-" + UUID.randomUUID());

        Assertions.assertThrows(VersionAlreadyExistsException.class,
                () -> sql.createArtifactVersion(GROUP_ID, artifactId, "2", ArtifactType.OPENAPI,
                        wrap(rejectedContent), null, Collections.emptyList(), false, false, null));

        Assertions.assertEquals(Optional.empty(), contentIdOf(rejectedContent));
    }

    /**
     * Another transaction stores the same new content after this transaction checked that it does not exist
     * and before it inserts it. The insert then loses the race and must find the winner's row, which this
     * transaction may only see by reading the latest committed data (REPEATABLE READ on MySQL).
     */
    @Test
    public void testSameContentCommittedByAnotherTransactionAfterTheExistenceCheck() throws Exception {
        String artifactId = createArtifactWithFirstVersion(openApi("first-" + UUID.randomUUID()));
        String otherArtifactId = "reaper-race-other-" + UUID.randomUUID();
        String sharedContent = openApi("shared-" + UUID.randomUUID());

        SqlContentRepository spy = Mockito.spy(originalContentRepository);
        AtomicBoolean armed = new AtomicBoolean(true);
        AtomicReference<ArtifactVersionMetaDataDto> winner = new AtomicReference<>();
        AtomicReference<Object> firstLookup = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            Object existing = invocation.callRealMethod();
            if (armed.compareAndSet(true, false)) {
                firstLookup.set(existing);
                runOnOtherThread(() -> winner.set(sql.createArtifact(GROUP_ID, otherArtifactId,
                        ArtifactType.OPENAPI, null, "1", wrap(sharedContent), null, Collections.emptyList(),
                        false, false, null).getValue()));
            }
            return existing;
        }).when(spy).contentIdFromHashRaw(any(Handle.class), any());
        sql.contentRepository = spy;

        ArtifactVersionMetaDataDto created = sql.createArtifactVersion(GROUP_ID, artifactId, "2",
                ArtifactType.OPENAPI, wrap(sharedContent), null, Collections.emptyList(), false, false, null);

        Assertions.assertEquals(Optional.empty(), firstLookup.get(),
                "the existence check must have run before the other transaction committed");
        Assertions.assertNotNull(winner.get(), "the other transaction must have stored the content");
        Assertions.assertEquals(winner.get().getContentId(), created.getContentId());
        Assertions.assertEquals(sharedContent,
                sql.getContentById(created.getContentId()).getContent().content());
    }
}
