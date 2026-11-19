package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integrity rules must evaluate the references that are stored, including the ones added by write hooks
 * (see {@link ReferenceAddingWriteHook}).
 */
@QuarkusTest
public class WriteHookReferencesRulesTest extends AbstractResourceTestBase {

    private static final String ORDER = """
            {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
             "properties": {"total": {"$ref": "money.json"}}}
            """;

    @Test
    public void hookAddedReferenceSatisfiesAllRefsMapped() throws Exception {
        String groupId = newGroupWithMoney();
        createGroupRule(groupId, RuleType.INTEGRITY, "ALL_REFS_MAPPED");

        var created = createArtifactWithReferences(groupId, "order", ArtifactType.JSON, ORDER,
                ContentTypes.APPLICATION_JSON, List.of());

        assertEquals("1", created.getVersion().getVersion());
        assertEquals(1, clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("order").versions()
                .byVersionExpression("1").references().get().size());
    }

    @Test
    public void hookAddedReferenceToMissingVersionViolatesRefsExist() throws Exception {
        String groupId = ReferenceAddingWriteHook.GROUP_PREFIX + UUID.randomUUID();
        createArtifactWithReferences(groupId, "other", ArtifactType.JSON, "{\"type\": \"string\"}",
                ContentTypes.APPLICATION_JSON, List.of());
        createGroupRule(groupId, RuleType.INTEGRITY, "REFS_EXIST");

        RuleViolationProblemDetails error = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifactWithReferences(groupId, "order", ArtifactType.JSON, ORDER,
                        ContentTypes.APPLICATION_JSON, List.of()));

        assertEquals("RuleViolationException", error.getName());
        assertEquals("money.json", error.getCauses().get(0).getContext());
    }

    @Test
    public void duplicateOfExplicitAndHookAddedReferenceViolatesNoDuplicates() throws Exception {
        String groupId = newGroupWithMoney();
        createGroupRule(groupId, RuleType.INTEGRITY, "NO_DUPLICATES");
        ArtifactReference explicit = ArtifactReference.builder().groupId(groupId).artifactId("money")
                .version("1").name(ReferenceAddingWriteHook.REFERENCE_NAME).build();

        RuleViolationProblemDetails error = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifactWithReferences(groupId, "order", ArtifactType.JSON, ORDER,
                        ContentTypes.APPLICATION_JSON, List.of(explicit)));

        assertEquals("RuleViolationException", error.getName());
        assertEquals("money.json", error.getCauses().get(0).getContext());
    }

    private String newGroupWithMoney() throws Exception {
        String groupId = ReferenceAddingWriteHook.GROUP_PREFIX + UUID.randomUUID();
        createArtifactWithReferences(groupId, "money", ArtifactType.JSON, "{\"type\": \"number\"}",
                ContentTypes.APPLICATION_JSON, List.of());
        return groupId;
    }
}
