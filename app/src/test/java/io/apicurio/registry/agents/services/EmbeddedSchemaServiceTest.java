package io.apicurio.registry.agents.services;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.extensions.ContentPlan;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * {@link EmbeddedSchemaService#planPromptTemplateEmbeddedSchemas} predicts the version a schema
 * registration would get and defers writing it to {@link ContentPlan#commit()}. If another write
 * takes that predicted version between planning and commit, the commit must fail rather than
 * register the schema under a different version than the plan's {@code $ref} already points at — see
 * issue #10603. Subclasses run these tests against every storage variant.
 */
@QuarkusTest
public class EmbeddedSchemaServiceTest {

    private static final int WRITERS = 4;

    @Inject
    EmbeddedSchemaService service;

    @Inject
    @Current
    RegistryStorage storage;

    private static final String PROMPT_V1 = """
            {
              "templateId": "test",
              "template": "Hello {{user}}",
              "outputSchema": { "type": "object", "properties": { "greeting": { "type": "string" } } }
            }
            """;

    private static final String PROMPT_V2 = """
            {
              "templateId": "test",
              "template": "Hello {{user}}",
              "outputSchema": { "type": "object", "properties": { "greeting": { "type": "string" },
                  "sessionId": { "type": "string" } } }
            }
            """;

    /** A prompt whose output schema has one property, named after the writer, so each writer's differs. */
    private static String promptFor(String property) {
        return """
                {
                  "templateId": "test",
                  "template": "Hello {{user}}",
                  "outputSchema": { "type": "object", "properties": { "%s": { "type": "string" } } }
                }
                """.formatted(property);
    }

    private ContentPlan plan(String groupId, String artifactId, String prompt) {
        return service.planPromptTemplateEmbeddedSchemas(storage, groupId, artifactId, ContentHandle.create(prompt),
                ContentTypes.APPLICATION_JSON, "test-owner");
    }

    @Test
    void commitFailsWhenThePredictedVersionIsTakenByAnotherWrite() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "taken-schema";
        String schemaArtifactId = artifactId + "-output-schema";

        // Plan v1: the schema artifact doesn't exist, so the plan predicts a new artifact at "1".
        plan(groupId, artifactId, PROMPT_V1).commit().run();
        assertEquals(1, storage.getArtifactVersions(groupId, schemaArtifactId).size());

        // Plan v2: the schema artifact now exists at version "1", so the plan predicts a new version "2".
        ContentPlan v2Plan = plan(groupId, artifactId, PROMPT_V2);

        // Another write takes "2" before this plan's commit runs.
        storage.createArtifactVersion(groupId, schemaArtifactId, null, "JSON",
                ContentWrapperDto.builder().content(ContentHandle.create("{\"type\": \"string\"}"))
                        .contentType(ContentTypes.APPLICATION_JSON).references(Collections.emptyList()).build(),
                EditableVersionMetaDataDto.builder().build(), Collections.emptyList(), false, false,
                "other-writer");
        assertEquals(2, storage.getArtifactVersions(groupId, schemaArtifactId).size());

        // The plan's commit must fail, not silently register under version "3": the parent's already
        // rewritten $ref points at "2", which now holds the other writer's content.
        Runnable v2Commit = v2Plan.commit();
        assertThrows(VersionAlreadyExistsException.class, v2Commit::run);

        // Nothing was added: the schema artifact still has only the two versions genuinely registered.
        assertEquals(2, storage.getArtifactVersions(groupId, schemaArtifactId).size());
    }

    @Test
    void commitSucceedsWhenNothingRacesThePlan() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "sequential-schema";
        String schemaArtifactId = artifactId + "-output-schema";

        plan(groupId, artifactId, PROMPT_V1).commit().run();
        plan(groupId, artifactId, PROMPT_V2).commit().run();

        assertEquals(2, storage.getArtifactVersions(groupId, schemaArtifactId).size());
    }

    /**
     * Several writers plan different schemas against the same base version, then commit at the same
     * moment from separate threads. All predicted version "2", so exactly one commit may succeed, and
     * version "2" must hold the winner's schema.
     */
    @Test
    void concurrentCommitsOfTheSamePredictedVersionHaveExactlyOneWinner() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "raced-schema";
        String schemaArtifactId = artifactId + "-output-schema";
        plan(groupId, artifactId, PROMPT_V1).commit().run();

        // Plan every writer before any commits, so all see base version "1" and predict "2".
        List<ContentPlan> plans = new ArrayList<>();
        for (int i = 0; i < WRITERS; i++) {
            ContentPlan plan = plan(groupId, artifactId, promptFor("writer" + i));
            assertEquals("2", plan.content().getReferences().get(0).getVersion());
            plans.add(plan);
        }

        CyclicBarrier start = new CyclicBarrier(WRITERS);
        ExecutorService executor = Executors.newFixedThreadPool(WRITERS);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (ContentPlan plan : plans) {
                results.add(executor.submit(() -> commitInRequestContext(plan, start)));
            }
            List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < WRITERS; i++) {
                if (results.get(i).get(60, TimeUnit.SECONDS)) {
                    winners.add(i);
                }
            }

            assertEquals(1, winners.size(), "exactly one commit of version 2 must succeed");
            assertEquals(Set.of("1", "2"), Set.copyOf(storage.getArtifactVersions(groupId, schemaArtifactId)));
            String stored = storage.getArtifactVersionContent(groupId, schemaArtifactId, "2").getContent()
                    .content();
            assertTrue(MAPPER.readTree(stored).path("properties").has("writer" + winners.get(0)),
                    "version 2 must hold the winning writer's schema: " + stored);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * @return true if the commit succeeded, false if it lost the race. Any other failure (for example a
     *         database deadlock) propagates, so the test reports it instead of counting it as a loss.
     */
    private static boolean commitInRequestContext(ContentPlan plan, CyclicBarrier start) throws Exception {
        ManagedContext context = Arc.container().requestContext();
        context.activate();
        try {
            start.await(30, TimeUnit.SECONDS);
            plan.commit().run();
            return true;
        } catch (VersionAlreadyExistsException lost) {
            assertEquals("2", lost.getVersion());
            return false;
        } finally {
            context.terminate();
        }
    }

    /**
     * A MODEL_SCHEMA plans both "input" and "output" as separate schema artifacts in one
     * {@link ContentPlan#commit()}. If "input" is new and commits fine but "output" then loses a
     * race for its predicted version, the whole commit must fail <em>and</em> undo "input": otherwise
     * "input" is left registered with nothing referencing it, because the parent is never written
     * when commit() throws.
     */
    @Test
    void commitRollsBackAnEarlierFieldWhenALaterFieldLosesTheRace() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "partial-model";
        String inputSchemaArtifactId = artifactId + "-input-schema";
        String outputSchemaArtifactId = artifactId + "-output-schema";
        String model = """
                {
                  "modelId": "m",
                  "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                  "output": { "type": "object", "properties": { "text": { "type": "string" } } }
                }
                """;

        ContentPlan plan = service.planModelSchemaEmbeddedSchemas(storage, groupId, artifactId,
                ContentHandle.create(model), ContentTypes.APPLICATION_JSON, "test-owner");

        // Another write takes the output schema's predicted artifact before this plan commits; the
        // input schema's predicted artifact is untouched, so input's write alone would succeed.
        storage.createArtifact(groupId, outputSchemaArtifactId, "JSON",
                EditableArtifactMetaDataDto.builder().build(), "1",
                ContentWrapperDto.builder().content(ContentHandle.create("{\"type\": \"string\"}"))
                        .contentType(ContentTypes.APPLICATION_JSON).references(Collections.emptyList()).build(),
                EditableVersionMetaDataDto.builder().build(), Collections.emptyList(), false, false,
                "other-writer");

        Runnable commit = plan.commit();
        assertThrows(VersionAlreadyExistsException.class, commit::run);

        // input must not be left behind just because output lost the race.
        assertThrows(ArtifactNotFoundException.class, () -> storage.getArtifactMetaData(groupId, inputSchemaArtifactId));
        // output is exactly what the other writer registered, untouched by the failed plan.
        assertEquals(1, storage.getArtifactVersions(groupId, outputSchemaArtifactId).size());
    }
}