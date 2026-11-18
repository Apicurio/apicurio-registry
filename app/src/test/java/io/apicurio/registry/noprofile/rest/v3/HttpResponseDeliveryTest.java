package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every response the server sends reaches the SDK client, over many requests on its pooled
 * keep-alive connections.
 * <p>
 * With Vert.x response compression enabled on the server, a response was intermittently lost after
 * the server had logged it as sent, and the client waited for its read timeout (issues 10855 and
 * 10992): locally within a few thousand requests. The short read timeout turns a lost response
 * into a failure in seconds instead of a minute.
 */
@QuarkusTest
public class HttpResponseDeliveryTest extends AbstractResourceTestBase {

    private static final int ARTIFACTS = 5000;
    private static final long READ_IDLE_TIMEOUT_MS = 15_000;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create().registryUrl(registryV3ApiUrl)
                .vertx(vertx).requestTimeout(CLIENT_CONNECT_TIMEOUT_MS, READ_IDLE_TIMEOUT_MS));
    }

    @Test
    void everyResponseReachesTheClient() throws Exception {
        String group = UUID.randomUUID().toString();
        String content = resourceToString("openapi-empty.json");
        for (int idx = 0; idx < ARTIFACTS; idx++) {
            String artifactId = "artifact-" + idx;
            CreateArtifactResponse created = createArtifact(group, artifactId, ArtifactType.OPENAPI,
                    content.replace("Empty API", artifactId), ContentTypes.APPLICATION_JSON);
            assertEquals(artifactId, created.getArtifact().getArtifactId());
        }
        assertEquals(ARTIFACTS, clientV3.groups().byGroupId(group).artifacts().get(config -> {
            config.queryParameters.limit = 1;
        }).getCount());
    }
}
