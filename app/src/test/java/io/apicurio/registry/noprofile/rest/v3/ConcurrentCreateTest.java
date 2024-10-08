package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.DeletionEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.impl.ConcurrentHashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;

@QuarkusTest
@TestProfile(DeletionEnabledProfile.class)
public class ConcurrentCreateTest extends AbstractResourceTestBase {

    @Test
    public void testMultipleArtifacts() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String groupId = TestUtils.generateGroupId();

        Set<String> created = new ConcurrentHashSet<>();
        CountDownLatch latch = new CountDownLatch(5);

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create artifacts
        for (int i = 0; i < 5; i++) {
            final int forkId = i;
            TestUtils.fork(() -> {
                String artifactId = "artifact-" + forkId;
                System.out.println("[Fork-" + forkId + "] Starting");
                System.out.println("[Fork-" + forkId + "] Artifact ID: " + artifactId);
                try {
                    CreateArtifact createArtifact = new CreateArtifact();
                    createArtifact.setArtifactId(artifactId);
                    createArtifact.setArtifactType(ArtifactType.OPENAPI);
                    createArtifact.setFirstVersion(new CreateVersion());
                    createArtifact.getFirstVersion().setContent(new VersionContent());
                    createArtifact.getFirstVersion().getContent().setContent(oaiArtifactContent);
                    createArtifact.getFirstVersion().getContent().setContentType("application/json");

                    // Create the artifact
                    CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts()
                            .post(createArtifact);
                    System.out.println("[Fork-" + forkId + "] Artifact created.");
                    Assertions.assertEquals(groupId, car.getVersion().getGroupId());
                    Assertions.assertEquals(artifactId, car.getVersion().getArtifactId());

                    // Fetch the artifact and make sure it really got created.
                    clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
                    clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                            .byVersionExpression("1");

                    created.add(artifactId);
                    System.out.println("[Fork-" + forkId + "] Succeeded");
                } catch (Exception e) {
                    System.out.println("[Fork-" + forkId + "] FAILED: " + e.getMessage());
                    Assertions.fail("Failure detected in fork " + forkId, e);
                }
                latch.countDown();
            });
        }

        latch.await();

        Assertions.assertEquals(5, created.size());
    }

    @Test
    public void testSameArtifact() throws Exception {
        String oaiArtifactContent = resourceToString("openapi-empty.json");
        String groupId = "testMultipleArtifacts";// TestUtils.generateGroupId();

        Set<Integer> created = new ConcurrentHashSet<>();
        CountDownLatch latch = new CountDownLatch(5);

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        // Create artifacts
        for (int i = 0; i < 5; i++) {
            final int forkId = i;
            TestUtils.fork(() -> {
                String artifactId = "artifact-" + forkId;
                System.out.println("[Fork-" + forkId + "] Starting");
                System.out.println("[Fork-" + forkId + "] Artifact ID: " + artifactId);
                try {
                    CreateArtifact createArtifact = new CreateArtifact();
                    createArtifact.setArtifactId(artifactId);
                    createArtifact.setArtifactType(ArtifactType.OPENAPI);
                    createArtifact.setFirstVersion(new CreateVersion());
                    createArtifact.getFirstVersion().setContent(new VersionContent());
                    createArtifact.getFirstVersion().getContent().setContent(oaiArtifactContent);
                    createArtifact.getFirstVersion().getContent().setContentType("application/json");

                    // Create the artifact
                    CreateArtifactResponse car = clientV3.groups().byGroupId(groupId).artifacts()
                            .post(createArtifact);
                    System.out.println("[Fork-" + forkId + "] Artifact created.");
                    Assertions.assertEquals(groupId, car.getVersion().getGroupId());
                    Assertions.assertEquals(artifactId, car.getVersion().getArtifactId());

                    // Fetch the artifact and make sure it really got created.
                    clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
                    clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                            .byVersionExpression("1");

                    created.add(forkId);
                    System.out.println("[Fork-" + forkId + "] Succeeded");
                } catch (Exception e) {
                    System.out.println("[Fork-" + forkId + "] FAILED: " + e.getMessage());
                    Assertions.fail("Failure detected in fork " + forkId, e);
                }
                latch.countDown();
            });
        }

        latch.await();

        Assertions.assertEquals(5, created.size());
    }

}
