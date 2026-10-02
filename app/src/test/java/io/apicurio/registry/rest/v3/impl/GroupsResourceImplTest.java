package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
     Verifies that requesting an OpenAPI artifact with HandleReferencesType.DEREFERENCE resolves its external @$ref
     pointers, regardless of whether the referenced artifact is stored as YAML (application/yaml) or JSON (application/json).

     Runs with @DeletionEnabledProfile so the created artifacts can be deleted after each test.
 *
 */


@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class GroupsResourceImplTest {

    @Inject
    GroupsResourceImpl groupsResource;

    @BeforeEach
    public void setupTestData(){

        String childAPI = """
            openapi: 3.0.3
            info:
              title: Common API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                CommonResponse:
                  type: object
                  properties:
                    id:
                      type: string
            """;

        String parentAPI = """
            openapi: 3.0.3
            info:
              title: Parent API
              version: 1.0.0
            paths:
              /example:
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            $ref: "common-api.yaml#/components/schemas/CommonResponse"
            """;
        CreateArtifact createChild = new CreateArtifact();
        createChild.setArtifactId("common-api");
        createChild.setArtifactType("OPENAPI");

        CreateVersion childVersion = new CreateVersion();
        childVersion.setVersion("1.0.0");
        VersionContent childContent = new VersionContent();
        childContent.setContent(childAPI);
        childContent.setContentType("application/yaml");
        childVersion.setContent(childContent);
        createChild.setFirstVersion(childVersion);

        groupsResource.createArtifact("default", null, false, false, createChild);

        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId("default");
        ref.setArtifactId("common-api");
        ref.setVersion("1.0.0");
        ref.setName("common-api.yaml#/components/schemas/CommonResponse");

        VersionContent parentContent = new VersionContent();
        parentContent.setContent(parentAPI);
        parentContent.setContentType("application/yaml");
        parentContent.setReferences(List.of(ref));

        CreateVersion parentVersion = new CreateVersion();
        parentVersion.setVersion("1.0.0");
        parentVersion.setContent(parentContent);

        CreateArtifact createParent = new CreateArtifact();
        createParent.setArtifactId("parent-api");
        createParent.setArtifactType("OPENAPI");
        createParent.setFirstVersion(parentVersion);

        groupsResource.createArtifact("default", null, false, false, createParent);
    }

    @AfterEach
    public void cleanup() {
        groupsResource.deleteArtifact("default", "parent-api");
        groupsResource.deleteArtifact("default", "common-api");
    }

    @Test
    public void testGetArtifactVersionContent_WithDereference_ResolvesReferenceToYamlArtifact() {
        Response response = groupsResource.getArtifactVersionContent(
                "default", "parent-api", "1.0.0",
               HandleReferencesType.DEREFERENCE,
                true
        );

        assertEquals(200, response.getStatus());
        String resposeString = response.getEntity().toString();
        assertFalse(resposeString.contains("\"$ref\":\"common-api.yaml#/components/schemas/CommonResponse\""), resposeString);
        assertTrue(resposeString.contains("\"$ref\":\"#/components/schemas/CommonResponse\""), resposeString);
        assertFalse(resposeString.contains("common-api.yaml"), resposeString);
    }
}
