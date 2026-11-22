package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Fixture for tests of the {@code references} parameter on {@code GET /ids/contentIds/{contentId}} and
 * {@code GET /ids/contentHashes/{contentHash}} (#11134): an OpenAPI artifact referencing another one.
 */
public abstract class AbstractIdsContentReferencesTest extends AbstractResourceTestBase {

    /** Shared with ServerSideDereferenceTest. */
    private static final String FIXTURES = "/io/apicurio/registry/noprofile/rest/";
    protected static final String WIDGET_REF = "paths.widgets.get.responses.200.content.json.schema.items.$ref";

    @Inject
    @Current
    RegistryStorage storage;

    protected String group;
    protected String withRefContent;
    private long contentId;
    private String contentHash;

    @BeforeEach
    public void createArtifacts() throws Exception {
        group = TestUtils.generateGroupId();
        createArtifact(group, "ReferencedTypes", ArtifactType.OPENAPI,
                resourceToString(FIXTURES + "openapi-referenced-types.json"), ContentTypes.APPLICATION_JSON);
        withRefContent = resourceToString(FIXTURES + "openapi-with-external-ref.json");
        List<ArtifactReference> refs = List.of(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(group)
                .artifactId("ReferencedTypes").version("1").build());
        CreateArtifactResponse created = createArtifactWithReferences(group, "WithExternalRef",
                ArtifactType.OPENAPI, withRefContent, ContentTypes.APPLICATION_JSON, refs);
        contentId = created.getVersion().getContentId();
        contentHash = storage.getContentById(contentId).getContentHash();
    }

    protected static RequestSpecification get() {
        return given().when();
    }

    protected String byId() {
        return "/registry/v3/ids/contentIds/" + contentId;
    }

    protected String byHash() {
        return "/registry/v3/ids/contentHashes/" + contentHash;
    }
}
