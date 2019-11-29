/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.ServiceInitializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractResourceTestBase extends AbstractRegistryTestBase {
    
    protected static final String CT_JSON = "application/json";
    protected static final String CT_PROTO = "application/x-protobuf";
    protected static final String CT_YAML = "application/x-yaml";

    @Inject
    Instance<ServiceInitializer> initializers;

    @BeforeEach
    protected void beforeEach() {
        // run all initializers::beforeEach
        initializers.stream().forEach(ServiceInitializer::beforeEach);

        // Delete all global rules
        given().when().delete("/rules").then().statusCode(204);
    }

    /**
     * Called to create an artifact by invoking the appropriate JAX-RS operation.
     * @param artifactId
     * @param artifactType
     * @param artifactContent
     * @throws Exception
     */
    protected void createArtifact(String artifactId, ArtifactType artifactType, String artifactContent) {
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("type", equalTo(artifactType.name()));
    }

    protected static <T> T retry(Callable<T> callable) throws Exception {
        int tries = 5;
        while (tries > 0) {
            try {
                return callable.call();
            } catch (Throwable t) {
                Thread.sleep(100L);
                tries--;
            }
        }
        Assertions.assertTrue(tries > 0, "Failed handle callable: " + callable);
        throw new IllegalStateException("Should not be here!");
    }

    // some impl details ...
    protected static void waitForSchema(RegistryService service, byte[] bytes) throws Exception {
        service.reset(); // clear any cache
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.get(); // magic byte
        long id = buffer.getLong(); // id
        ArtifactMetaData amd = retry(() -> service.getArtifactMetaDataByGlobalId(id));
        Assertions.assertNotNull(amd); // wait for global id to populate
    }

}
