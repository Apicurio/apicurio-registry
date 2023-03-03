package io.apicurio.registry.events;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

@QuarkusTest
@TestProfile(HttpEventsProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class HttpEventsTest extends AbstractResourceTestBase {

    @Test
    @Timeout(value = 65, unit = TimeUnit.SECONDS)
    public void testHttpEvents() throws TimeoutException {

        CompletableFuture<HttpServer> serverFuture = new CompletableFuture<>();
        List<String> events = new CopyOnWriteArrayList<>();

        HttpServer server = Vertx.vertx().createHttpServer(new HttpServerOptions()
                        .setPort(8976))
                .requestHandler(req -> {
                    events.add(req.headers().get("ce-type"));
                    req.response().setStatusCode(200).end();
                })
                .listen(createdServer -> {
                    if (createdServer.succeeded()) {
                        serverFuture.complete(createdServer.result());
                    } else {
                        serverFuture.completeExceptionally(createdServer.cause());
                    }
                });

        TestUtils.waitFor("proxy is ready", Duration.ofSeconds(1).toMillis(), Duration.ofSeconds(30).toMillis(), serverFuture::isDone);

        try {
            InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
            Assertions.assertNotNull(jsonSchema);
            String content = IoUtil.toString(jsonSchema);

            String artifactId = TestUtils.generateArtifactId();

            try {
                createArtifact(artifactId, ArtifactType.JSON, content);
                createArtifactVersion(artifactId, ArtifactType.JSON, content);
            } catch (Exception e) {
                Assertions.fail(e);
            }

            TestUtils.waitFor("Events to be produced", 200, 60 * 1000, () -> events.size() == 2);

            assertLinesMatch(
                    Arrays.asList(RegistryEventType.ARTIFACT_CREATED.cloudEventType(), RegistryEventType.ARTIFACT_UPDATED.cloudEventType()),
                    events);
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }
}