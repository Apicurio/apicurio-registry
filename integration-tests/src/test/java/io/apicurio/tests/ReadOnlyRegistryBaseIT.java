package io.apicurio.tests;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for integration tests against read-only storage variants (e.g., KubernetesOps, GitOps).
 * Overrides the standard setup to avoid write operations that would fail on read-only storage.
 */
public class ReadOnlyRegistryBaseIT extends ApicurioRegistryBaseIT {

    @Override
    @BeforeAll
    void prepareRestAssured() {
        vertx = Vertx.vertx();
        registryClient = createRegistryClient(vertx);
        RestAssured.baseURI = getRegistryV3ApiUrl();
        logger.info("RestAssured configured with {}", RestAssured.baseURI);
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.urlEncodingEnabled = false;
        // Do NOT delete global rules - storage is read-only
    }
}
