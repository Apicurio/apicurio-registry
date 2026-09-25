package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.Arrays;
import java.util.List;

/**
 * Test to verify that the registry can handle duplicate references in content.
 * This reproduces the issue reported in GitHub issue #6757 where Debezium
 * sends schemas with duplicate references for nested types (e.g., PostGIS Point geometry).
 */
@QuarkusTest
@ExtendWith(DuplicateReferencesTest.RuleViolationWatcher.class)
public class DuplicateReferencesTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = DuplicateReferencesTest.class.getSimpleName();

    /**
     * Test watcher that provides detailed error information for RuleViolationProblemDetails exceptions.
     */
    public static class RuleViolationWatcher implements TestWatcher {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            // Check if the exception or its cause is a RuleViolationProblemDetails
            // Use class name comparison instead of instanceof to avoid classloader issues in Quarkus tests
            Throwable current = cause;
            while (current != null) {
                String className = current.getClass().getName();
                if (className.equals("io.apicurio.registry.rest.client.models.RuleViolationProblemDetails")) {
                    try {
                        // Use reflection to access the properties since we can't cast due to classloader differences
                        Class<?> violationClass = current.getClass();
                        Object title = violationClass.getMethod("getTitle").invoke(current);
                        Object detail = violationClass.getMethod("getDetail").invoke(current);
                        Object status = violationClass.getMethod("getStatus").invoke(current);
                        Object causes = violationClass.getMethod("getCauses").invoke(current);

                        System.err.println("========================================");
                        System.err.println("RuleViolationProblemDetails detected:");
                        System.err.println("  Title: " + title);
                        System.err.println("  Detail: " + detail);
                        System.err.println("  Status: " + status);

                        if (causes != null && causes instanceof List) {
                            List<?> causeList = (List<?>) causes;
                            if (!causeList.isEmpty()) {
                                System.err.println("  Causes:");
                                for (Object c : causeList) {
                                    Class<?> causeClass = c.getClass();
                                    Object description = causeClass.getMethod("getDescription").invoke(c);
                                    Object causeContext = causeClass.getMethod("getContext").invoke(c);
                                    System.err.println("    - " + description + " (context: " + causeContext + ")");
                                }
                            }
                        }
                        System.err.println("========================================");
                    } catch (Exception e) {
                        System.err.println("Failed to extract RuleViolationProblemDetails: " + e.getMessage());
                    }
                    break;
                }
                current = current.getCause();
            }
        }
    }

    /**
     * Test that creating an artifact with duplicate references in the reference list
     * does not fail. This simulates the Debezium use case where nested schemas
     * may result in the same reference being included multiple times.
     */
    @Test
    public void testCreateArtifactWithDuplicateReferences() throws Exception {
        String referencedArtifactId = "referenced-schema";
        String mainArtifactId = "main-schema-with-duplicates";

        // First, create a referenced artifact that will be referenced (possibly multiple times)
        String referencedContent = "{\"type\":\"record\",\"name\":\"Point\",\"fields\":[{\"name\":\"x\",\"type\":\"double\"},{\"name\":\"y\",\"type\":\"double\"}]}";

        CreateArtifact createReferenced = new CreateArtifact();
        createReferenced.setArtifactId(referencedArtifactId);
        createReferenced.setArtifactType(ArtifactType.AVRO);
        createReferenced.setFirstVersion(new CreateVersion());
        createReferenced.getFirstVersion().setContent(new VersionContent());
        createReferenced.getFirstVersion().getContent().setContent(referencedContent);
        createReferenced.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);

        CreateArtifactResponse referencedResponse = clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createReferenced);
        Assertions.assertNotNull(referencedResponse);
        String version = referencedResponse.getVersion().getVersion();

        // Now create the main artifact with DUPLICATE references to the same artifact
        // This simulates what Debezium does when processing nested schemas
        ArtifactReference reference1 = new ArtifactReference();
        reference1.setGroupId(GROUP_ID);
        reference1.setArtifactId(referencedArtifactId);
        reference1.setVersion(version);
        reference1.setName("io.debezium.data.geometry.Point");

        // Create a second reference that is identical to the first (duplicate)
        ArtifactReference reference2 = new ArtifactReference();
        reference2.setGroupId(GROUP_ID);
        reference2.setArtifactId(referencedArtifactId);
        reference2.setVersion(version);
        reference2.setName("io.debezium.data.geometry.Point");

        // The references list contains duplicates
        List<ArtifactReference> duplicateReferences = Arrays.asList(reference1, reference2);

        // Main schema content that uses the referenced schema
        String mainContent = "{\"type\":\"record\",\"name\":\"TableValue\",\"fields\":[{\"name\":\"cpoint\",\"type\":\"io.debezium.data.geometry.Point\"}]}";

        // This should NOT throw a ConflictException about duplicate references
        // The registry should be tolerant of duplicate references in the input list
        CreateArtifact createMain = new CreateArtifact();
        createMain.setArtifactId(mainArtifactId);
        createMain.setArtifactType(ArtifactType.AVRO);
        createMain.setFirstVersion(new CreateVersion());
        createMain.getFirstVersion().setContent(new VersionContent());
        createMain.getFirstVersion().getContent().setContent(mainContent);
        createMain.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createMain.getFirstVersion().getContent().setReferences(duplicateReferences);  // Contains duplicate references

        CreateArtifactResponse mainResponse = clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createMain);

        // Verify the artifact was created successfully
        Assertions.assertNotNull(mainResponse);
        Assertions.assertEquals(GROUP_ID, mainResponse.getArtifact().getGroupId());
        Assertions.assertEquals(mainArtifactId, mainResponse.getArtifact().getArtifactId());

        // Verify we can retrieve the artifact's references
        List<io.apicurio.registry.rest.client.models.ArtifactReference> retrievedRefs = clientV3.groups()
            .byGroupId(GROUP_ID)
            .artifacts()
            .byArtifactId(mainArtifactId)
            .versions()
            .byVersionExpression(mainResponse.getVersion().getVersion())
            .references()
            .get();

        // The stored references should be deduplicated (only one reference should be stored)
        Assertions.assertNotNull(retrievedRefs);
        Assertions.assertEquals(1, retrievedRefs.size(),
            "Duplicate references should be deduplicated before storage");
    }

    /**
     * Test that creating a version with duplicate references also works.
     * This tests the createArtifactVersion path.
     */
    @Test
    public void testCreateVersionWithDuplicateReferences() throws Exception {
        String referencedArtifactId = "referenced-schema-v2";
        String mainArtifactId = "main-schema-versions";

        // Create a referenced artifact
        String referencedContent = "{\"type\":\"record\",\"name\":\"Point\",\"fields\":[{\"name\":\"x\",\"type\":\"double\"}]}";

        CreateArtifact createReferenced = new CreateArtifact();
        createReferenced.setArtifactId(referencedArtifactId);
        createReferenced.setArtifactType(ArtifactType.AVRO);
        createReferenced.setFirstVersion(new CreateVersion());
        createReferenced.getFirstVersion().setContent(new VersionContent());
        createReferenced.getFirstVersion().getContent().setContent(referencedContent);
        createReferenced.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);

        CreateArtifactResponse referencedResponse = clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createReferenced);
        String version = referencedResponse.getVersion().getVersion();

        // Create the initial artifact (version 1) without references
        String initialContent = "{\"type\":\"record\",\"name\":\"Simple\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}";

        CreateArtifact createInitial = new CreateArtifact();
        createInitial.setArtifactId(mainArtifactId);
        createInitial.setArtifactType(ArtifactType.AVRO);
        createInitial.setFirstVersion(new CreateVersion());
        createInitial.getFirstVersion().setContent(new VersionContent());
        createInitial.getFirstVersion().getContent().setContent(initialContent);
        createInitial.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);

        clientV3.groups().byGroupId(GROUP_ID).artifacts().post(createInitial);

        // Create duplicate references
        ArtifactReference ref1 = new ArtifactReference();
        ref1.setGroupId(GROUP_ID);
        ref1.setArtifactId(referencedArtifactId);
        ref1.setVersion(version);
        ref1.setName("Point");

        ArtifactReference ref2 = new ArtifactReference();
        ref2.setGroupId(GROUP_ID);
        ref2.setArtifactId(referencedArtifactId);
        ref2.setVersion(version);
        ref2.setName("Point");

        List<ArtifactReference> duplicateReferences = Arrays.asList(ref1, ref2);

        // Create version 2 with duplicate references - should succeed
        String v2Content = "{\"type\":\"record\",\"name\":\"WithPoint\",\"fields\":[{\"name\":\"location\",\"type\":\"Point\"}]}";

        CreateVersion createV2 = new CreateVersion();
        createV2.setContent(new VersionContent());
        createV2.getContent().setContent(v2Content);
        createV2.getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createV2.getContent().setReferences(duplicateReferences);

        var v2Response = clientV3.groups()
            .byGroupId(GROUP_ID)
            .artifacts()
            .byArtifactId(mainArtifactId)
            .versions()
            .post(createV2);

        Assertions.assertNotNull(v2Response);
        Assertions.assertEquals("2", v2Response.getVersion());

        // Verify the stored content has deduplicated references
        List<io.apicurio.registry.rest.client.models.ArtifactReference> retrievedRefs = clientV3.groups()
            .byGroupId(GROUP_ID)
            .artifacts()
            .byArtifactId(mainArtifactId)
            .versions()
            .byVersionExpression("2")
            .references()
            .get();

        Assertions.assertEquals(1, retrievedRefs.size(),
            "Duplicate references should be deduplicated before storage");
    }
}
