package io.apicurio.registry.noprofile.compatibility;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.app.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.rules.compatibility.CompatibilityCheckNotSupportedException;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Verifies that a COMPATIBILITY rule is not silently satisfied by artifact types whose compatibility checker
 * is a stub.
 * <p>
 * Before the fix, {@link CompatibilityRuleExecutor} delegated straight to
 * {@code NoopCompatibilityChecker}, which returns "compatible" unconditionally. An operator enforcing
 * BACKWARD or FULL_TRANSITIVE on such a type received a passing verdict for an outright breaking change.
 * </p>
 */
@QuarkusTest
public class UnsupportedTypeCompatibilityRuleTest extends AbstractResourceTestBase {

    private static final String XML_V1 = "<?xml version=\"1.0\"?><order><id>1</id><total>9.99</total></order>";
    private static final String XML_V2 = "<?xml version=\"1.0\"?><invoice><ref>1</ref></invoice>";

    private static final String GRAPHQL_V1 = "type Query { id: String name: String }";
    private static final String GRAPHQL_V2 = "type Mutation { unrelated: Int }";

    private static final String AVRO_V1 = "{\"type\":\"record\",\"namespace\":\"com.example\",\"name\":\"FullName\","
            + "\"fields\":[{\"name\":\"first\",\"type\":\"string\"},{\"name\":\"last\",\"type\":\"string\"}]}";
    private static final String AVRO_V2 = "{\"type\": \"string\"}";

    @Inject
    CompatibilityRuleExecutor compatibility;

    private static TypedContent xml(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_XML);
    }

    private static TypedContent json(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private RuleContext context(String artifactType, String level, TypedContent existing,
            TypedContent proposed) {
        return new RuleContext("TestGroup", "TestArtifact", artifactType, level,
                Collections.singletonList(existing), proposed, Collections.emptyList(),
                Collections.emptyMap(), null);
    }

    /**
     * The core regression: XML has no compatibility checker, so a wholly incompatible replacement used to
     * be reported as compatible. It must now be rejected instead.
     */
    @Test
    public void testUnsupportedTypeRejectsNonNoneLevel() {
        RuleContext ctx = context(ArtifactType.XML, CompatibilityLevel.BACKWARD.name(), xml(XML_V1),
                xml(XML_V2));

        CompatibilityCheckNotSupportedException ex = Assertions
                .assertThrows(CompatibilityCheckNotSupportedException.class,
                        () -> compatibility.execute(ctx));

        Assertions.assertTrue(ex.getMessage().contains(ArtifactType.XML),
                "Error message should name the offending artifact type, was: " + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("apicurio.compat.allow-unsupported-types.enabled"),
                "Error message should tell the operator how to opt out, was: " + ex.getMessage());
    }

    /**
     * Every transitive/non-transitive level other than NONE must be rejected, not just BACKWARD.
     */
    @Test
    public void testUnsupportedTypeRejectsEveryEnforcingLevel() {
        for (CompatibilityLevel level : CompatibilityLevel.values()) {
            if (level == CompatibilityLevel.NONE) {
                continue;
            }
            RuleContext ctx = context(ArtifactType.ASYNCAPI, level.name(), json("{}"), json("{}"));
            Assertions.assertThrows(CompatibilityCheckNotSupportedException.class,
                    () -> compatibility.execute(ctx),
                    "Level " + level + " should be rejected for an artifact type without a checker");
        }
    }

    /**
     * NONE means "rule disabled" and must keep short-circuiting before the capability check, otherwise
     * simply having the rule present would break writes for these types.
     */
    @Test
    public void testUnsupportedTypeAllowsNoneLevel() {
        RuleContext ctx = context(ArtifactType.XML, CompatibilityLevel.NONE.name(), xml(XML_V1),
                xml(XML_V2));

        Assertions.assertDoesNotThrow(() -> compatibility.execute(ctx));
    }

    /**
     * Regression guard: types that do have a checker must keep evaluating content, not be short-circuited
     * by the new capability gate.
     */
    @Test
    public void testSupportedTypeStillEvaluatesContent() {
        RuleContext incompatible = context(ArtifactType.AVRO, CompatibilityLevel.BACKWARD.name(),
                json(AVRO_V1), json(AVRO_V2));
        Assertions.assertThrows(RuleViolationException.class,
                () -> compatibility.execute(incompatible));

        RuleContext compatible = context(ArtifactType.AVRO, CompatibilityLevel.BACKWARD.name(),
                json(AVRO_V1), json(AVRO_V1));
        Assertions.assertDoesNotThrow(() -> compatibility.execute(compatible));
    }

    /**
     * End-to-end check over the v3 REST API, mirroring how an operator hits this: configure an artifact
     * rule, then push a breaking version.
     * <p>
     * The client type is {@code RuleViolationProblemDetails} rather than plain {@code ProblemDetails}
     * because the v3 OpenAPI declares the 400 response of createArtifactVersion as
     * {@code RuleViolationBadRequest}; every 400 from this operation deserializes to that shape. The
     * rule-specific fields are simply left unset here.
     * </p>
     */
    @Test
    public void testRestApiRejectsBreakingChangeForUnsupportedType() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.GRAPHQL, GRAPHQL_V1,
                ContentTypes.APPLICATION_GRAPHQL);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        CreateVersion breaking = TestUtils.clientCreateVersion(GRAPHQL_V2,
                ContentTypes.APPLICATION_GRAPHQL);

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                        .post(breaking));

        Assertions.assertEquals(400, error.getStatus());
        Assertions.assertTrue(error.getDetail().contains(ArtifactType.GRAPHQL),
                "Error detail should name the artifact type, was: " + error.getDetail());
        Assertions.assertTrue(
                error.getDetail().contains("apicurio.compat.allow-unsupported-types.enabled"),
                "Error detail should tell the operator how to opt out, was: " + error.getDetail());
    }
}
