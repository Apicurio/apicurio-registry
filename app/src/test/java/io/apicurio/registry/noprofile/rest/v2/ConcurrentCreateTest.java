package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class ConcurrentCreateTest extends AbstractResourceTestBase {

    @Test
    public void testMultipleArtifacts() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String groupId = "testMultipleArtifacts";// TestUtils.generateGroupId();

        Set<String> created = new HashSet<>();
        Set<String> failed = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(5);

        clientV2.createArtifactGroup(GroupMetaData.builder().id(groupId).build());

        // Create artifacts
        for (int i = 0; i < 5; i++) {
            final int forkId = i;
            TestUtils.fork(() -> {
                String artifactId = "artifact-" + forkId;
                System.out.println("[Fork-" + forkId + "] Starting");
                System.out.println("[Fork-" + forkId + "] Artifact ID: " + artifactId);
                try {
                    InputStream data = new ByteArrayInputStream(oaiArtifactContent.getBytes());
                    ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, data);
                    System.out.println("[Fork-" + forkId + "] Artifact created.");
                    System.out.println("[Fork-" + forkId + "] Global ID  : " + amd.getGlobalId() + "\n" +
                            "         Content ID : " + amd.getContentId() + "\n" +
                            "         GroupId    : " + amd.getGroupId() + "\n" +
                            "         ArtifactId : " + amd.getId() + "\n" +
                            "         Version    : " + amd.getVersion()
                    );
                    Assertions.assertNotNull(amd);
                    Assertions.assertEquals(groupId, amd.getGroupId());
                    System.out.println("[Fork-" + forkId + "] " + artifactId + " vs " + amd.getId());
                    Assertions.assertEquals(artifactId, amd.getId());
                    System.out.println("[Fork-" + forkId + "] Completed successfully.");

                    amd = clientV2.getArtifactMetaData(groupId, artifactId);
                    Assertions.assertNotNull(amd);
                    Assertions.assertEquals(groupId, amd.getGroupId());
                    Assertions.assertEquals(artifactId, amd.getId());

                    created.add(artifactId);
                } catch (Exception e) {
                    System.out.println("[Fork-" + forkId + "] FAILED: " + e.getMessage());
                    failed.add(artifactId);
                }
                latch.countDown();
            });
        }

        latch.await();

        Assertions.assertEquals(5, created.size());
        Assertions.assertEquals(0, failed.size());

        ArtifactSearchResults results = clientV2.searchArtifacts(null, null, null, null, null, SortBy.createdOn, SortOrder.asc, 0, 100);
        System.out.println("===");
        results.getArtifacts().forEach(artifact -> {
            System.out.println("  - " + artifact.getId());
        });
        System.out.println("===");

        results = clientV2.listArtifactsInGroup(groupId);
        System.out.println("+++");
        results.getArtifacts().forEach(artifact -> {
            System.out.println("  - " + artifact.getId());
        });
        System.out.println("+++");

        // Search the groupId to ensure the correct # of artifacts.

        // Note: we need to retry because creation of the artifacts is forked, so we can get
        // here before the artifacts have actually been created.
//        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("groupId", groupId)
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(5));
//        });
    }

}
