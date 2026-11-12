package io.apicurio.registry.noprofile.rest.v3;

import tools.jackson.databind.JsonNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;

/**
 * Embedded schema extraction (issue #10603) plans registration without writing anything, and the
 * caller commits only after every check has passed, and never on a dry run. These tests check that
 * a request rejected after planning — by a rule, by another hook, or implicitly by a dry run — never
 * leaves a registered schema artifact or version behind.
 */
@QuarkusTest
public class EmbeddedSchemaExtractionRejectionTest extends AbstractResourceTestBase {

    private static final String PROMPT_TEMPLATE = "PROMPT_TEMPLATE";

    private static final String VALID_PROMPT = """
            {
              "templateId": "test",
              "template": "Hello {{user}}",
              "outputSchema": {
                "type": "object",
                "properties": {
                  "greeting": { "type": "string" }
                }
              }
            }
            """;

    /** Missing "template": fails the VALIDITY FULL check run after embedded schemas are planned. */
    private static final String INVALID_PROMPT = """
            {
              "templateId": "test",
              "outputSchema": {
                "type": "object",
                "properties": {
                  "greeting": { "type": "string" }
                }
              }
            }
            """;

    @Test
    public void aRuleRejectionLeavesNoSchemaArtifactBehind() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-rule-rejected";
        String schemaArtifactId = artifactId + "-output-schema";

        createGlobalRule(RuleType.VALIDITY, "FULL");

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifact(groupId, artifactId, PROMPT_TEMPLATE, INVALID_PROMPT,
                        ContentTypes.APPLICATION_JSON));
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertEquals("/template", error.getCauses().get(0).getContext());

        ProblemDetails notFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                        .versions().get());
        Assertions.assertEquals(404, notFound.getStatus());
    }

    @Test
    public void anotherHooksRejectionLeavesNoSchemaArtifactBehind() {
        String groupId = RecordingWriteHook.GROUP_PREFIX + UUID.randomUUID();
        String artifactId = "prompt-hook-rejected";
        String schemaArtifactId = artifactId + "-output-schema";
        // The marker is inside "templateId", which no validator or the schema extraction inspects, so
        // only RecordingWriteHook's beforePublish reacts to it.
        String content = VALID_PROMPT.replace("\"test\"", "\"" + RecordingWriteHook.REJECT_MARKER + "\"");

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, PROMPT_TEMPLATE, content,
                ContentTypes.APPLICATION_JSON);
        Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact));

        ProblemDetails notFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                        .versions().get());
        Assertions.assertEquals(404, notFound.getStatus());
    }

    @Test
    public void aDryRunLeavesNoSchemaArtifactBehind() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-dry-run";
        String schemaArtifactId = artifactId + "-output-schema";

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, PROMPT_TEMPLATE, VALID_PROMPT,
                ContentTypes.APPLICATION_JSON);
        // A dry run still returns the rewritten content and a $ref to the schema it would register...
        var response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact,
                config -> config.queryParameters.dryRun = true);
        Assertions.assertEquals("1", response.getVersion().getVersion());

        // ...but the schema itself, and the artifact itself, must not exist.
        ProblemDetails artifactNotFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
        Assertions.assertEquals(404, artifactNotFound.getStatus());
        ProblemDetails schemaNotFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(schemaArtifactId)
                        .versions().get());
        Assertions.assertEquals(404, schemaNotFound.getStatus());
    }

    @Test
    public void mismatchedInputAndOutputResultDoesNotLeaveAPartialSchemaBehind() {
        // Both "input" and "output" are extractable, so both are planned; the missing "modelId" fails
        // the VALIDITY FULL check run after planning. Neither schema artifact must exist afterwards,
        // not just the one that would have been planned last.
        String groupId = TestUtils.generateGroupId();
        String artifactId = "model-partial-rejected";
        String inputSchemaArtifactId = artifactId + "-input-schema";
        String outputSchemaArtifactId = artifactId + "-output-schema";

        createGlobalRule(RuleType.VALIDITY, "FULL");
        String invalidModel = """
                {
                  "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                  "output": { "type": "object", "properties": { "text": { "type": "string" } } }
                }
                """;

        Assertions.assertThrows(RuleViolationProblemDetails.class, () -> createArtifact(groupId, artifactId,
                "MODEL_SCHEMA", invalidModel, ContentTypes.APPLICATION_JSON));

        ProblemDetails inputNotFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(inputSchemaArtifactId)
                        .versions().get());
        Assertions.assertEquals(404, inputNotFound.getStatus());
        ProblemDetails outputNotFound = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(outputSchemaArtifactId)
                        .versions().get());
        Assertions.assertEquals(404, outputNotFound.getStatus());
    }

    /**
     * Concurrent new versions of one prompt template, each with a different embedded schema. Several
     * requests predict the same next schema version; at most one may commit it, the others fail with
     * 409. Whatever the interleaving: every created parent version references a schema version holding
     * its own schema, and failed requests leave no schema version behind.
     */
    @Test
    public void concurrentVersionsNeverReferenceAnotherRequestsSchema() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-concurrent";
        String schemaArtifactId = artifactId + "-output-schema";
        createArtifact(groupId, artifactId, PROMPT_TEMPLATE, VALID_PROMPT, ContentTypes.APPLICATION_JSON);

        int writers = 6;
        CyclicBarrier start = new CyclicBarrier(writers);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Outcome>> results = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                String property = "writer" + i;
                results.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    try {
                        return new Outcome(clientV3.groups().byGroupId(groupId).artifacts()
                                .byArtifactId(artifactId).versions()
                                .post(newVersion(VALID_PROMPT.replace("greeting", property))), null);
                    } catch (ProblemDetails failure) {
                        return new Outcome(null, failure);
                    }
                }));
            }

            int created = 0;
            int conflicts = 0;
            for (int i = 0; i < writers; i++) {
                Outcome outcome = results.get(i).get(60, TimeUnit.SECONDS);
                if (outcome.failure() != null) {
                    // Only the lost race on the predicted schema version may fail the request
                    ProblemDetails failure = outcome.failure();
                    Assertions.assertEquals(409, failure.getStatus(), failure.getDetail());
                    Assertions.assertEquals("VersionAlreadyExistsException", failure.getName(), failure.getDetail());
                    Assertions.assertTrue(failure.getDetail().contains(schemaArtifactId), failure.getDetail());
                    conflicts++;
                    continue;
                }
                created++;
                String version = outcome.version().getVersion();
                JsonNode parent = MAPPER.readTree(getContent(groupId, artifactId, version));
                String ref = parent.path("outputSchema").path("$ref").asString();
                String refPrefix = groupId + ":" + schemaArtifactId + ":";
                Assertions.assertTrue(ref.startsWith(refPrefix), "unexpected $ref: " + ref);
                String schemaVersion = ref.substring(refPrefix.length());
                JsonNode schema = MAPPER.readTree(getContent(groupId, schemaArtifactId, schemaVersion));
                Assertions.assertTrue(schema.path("properties").has("writer" + i),
                        "parent version " + version + " of writer" + i + " references " + ref
                                + ", which holds " + schema);
            }

            Assertions.assertEquals(writers, created + conflicts);
            Assertions.assertTrue(created >= 1, "at least one concurrent version must be created");
            // The first version created schema "1"; each created parent version added exactly one schema
            // version, and failed requests added none.
            Assertions.assertEquals(1 + created, clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(schemaArtifactId).versions().get().getCount());
            Assertions.assertEquals(1 + created, clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().get().getCount());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /** One concurrent request's result: the created version, or the error it failed with. */
    private record Outcome(VersionMetaData version, ProblemDetails failure) {
    }

    private static CreateVersion newVersion(String content) {
        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        return createVersion;
    }

    private String getContent(String groupId, String artifactId, String version) throws Exception {
        try (InputStream stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).content().get()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
