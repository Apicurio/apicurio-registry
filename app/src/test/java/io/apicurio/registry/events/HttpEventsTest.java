package io.apicurio.registry.events;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Disabled;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;

@QuarkusTest
@TestProfile(HttpEventsProfile.class)
@Disabled
public class HttpEventsTest extends AbstractResourceTestBase {

    @Test
    @Timeout(value = 65, unit = TimeUnit.SECONDS)
    public void testHttpEvents() throws TimeoutException {

        HttpServer server = null;
        try {
            List<String> events = new CopyOnWriteArrayList<>();
            server = Vertx.vertx().createHttpServer(new HttpServerOptions().setPort(8888))
            .requestHandler(req -> {
                events.add(req.headers().get("ce-type"));
                req.response().setStatusCode(200).end();
            }).listen(ar -> {
                if (ar.succeeded()) {

                    InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
                    Assertions.assertNotNull(jsonSchema);
                    String content = IoUtil.toString(jsonSchema);

                    String artifactId = TestUtils.generateArtifactId();

                    try {
                        createArtifact(artifactId, ArtifactType.JSON, content);
                        createArtifactVersion(artifactId, ArtifactType.JSON, content);
                    } catch ( Exception e ) {
                        Assertions.fail(e);
                    }

                } else {
                    Assertions.fail(ar.cause());
                }
            });

            TestUtils.waitFor("Events to be produced", 200, 60 * 1000, () -> {
                return events.size() == 2;
            });

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