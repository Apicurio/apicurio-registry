package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clients creating the same artifact at the same moment through REST v3 (#11412): one creates it, the
 * others get the find-or-create result, and the database stays usable for the next request.
 */
@QuarkusTest
public class ConcurrentCreateArtifactTest extends AbstractResourceTestBase {

    private static final int CLIENTS = 8;

    private static CreateArtifact createRequest(String artifactId, String content) {
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setContent(versionContent);
        CreateArtifact create = new CreateArtifact();
        create.setArtifactId(artifactId);
        create.setArtifactType(ArtifactType.AVRO);
        create.setFirstVersion(firstVersion);
        return create;
    }

    private static String schema(String name) {
        return "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[{\"name\":\"f\",\"type\":\"string\"}]}";
    }

    @Test
    void concurrentFindOrCreateReturnsTheSameVersion() throws Exception {
        String groupId = "race-" + UUID.randomUUID();
        String content = schema("Shared");

        List<CreateArtifactResponse> responses = postConcurrently(groupId, createRequest("shared", content));

        assertEquals(CLIENTS, responses.size());
        for (CreateArtifactResponse response : responses) {
            assertEquals("shared", response.getArtifact().getArtifactId());
            assertEquals("1", response.getVersion().getVersion());
        }
        assertEquals(1, responses.stream().map(r -> r.getVersion().getGlobalId()).distinct().count());
        assertEquals(1, clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("shared").versions()
                .get().getCount());

        // The next request in the group still works.
        CreateArtifactResponse next = clientV3.groups().byGroupId(groupId).artifacts()
                .post(createRequest("next", schema("Next")));
        assertEquals("1", next.getVersion().getVersion());
    }

    private List<CreateArtifactResponse> postConcurrently(String groupId, CreateArtifact create)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS);
        try {
            CountDownLatch ready = new CountDownLatch(CLIENTS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<CreateArtifactResponse>> futures = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return clientV3.groups().byGroupId(groupId).artifacts().post(create,
                            config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            List<CreateArtifactResponse> responses = new ArrayList<>();
            for (Future<CreateArtifactResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }
}
