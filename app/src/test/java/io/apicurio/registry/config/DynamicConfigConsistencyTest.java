package io.apicurio.registry.config;

import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every {@code @Dynamic} property must have a {@code .dynamic.allow} entry, and the v2 admin API must apply
 * the same value checks as v3 when a property is updated.
 */
@QuarkusTest
public class DynamicConfigConsistencyTest extends AbstractResourceTestBase {

    private static final String AGENTS_UI = "apicurio.ui.features.agents.enabled";
    private static final String LOG_LEVEL = "apicurio.log.level";

    @Inject
    DynamicConfigPropertyIndex dynamicPropertyIndex;

    @Inject
    Config config;

    @AfterEach
    void reset() {
        given().pathParam("name", AGENTS_UI).delete("/registry/v3/admin/config/properties/{name}");
        given().pathParam("name", LOG_LEVEL).delete("/registry/v3/admin/config/properties/{name}");
    }

    @Test
    void everyDynamicPropertyHasAnAllowEntry() {
        List<String> missing = dynamicPropertyIndex.getPropertyNames().stream()
                .filter(name -> config.getOptionalValue(name + ".dynamic.allow", String.class).isEmpty())
                .sorted().collect(Collectors.toList());
        assertEquals(List.of(), missing);
    }

    @Test
    void v2CannotEnableExperimentalPropertyWithoutGate() {
        given().contentType(CT_JSON).body("{\"value\":\"true\"}").pathParam("name", AGENTS_UI)
                .put("/registry/v2/admin/config/properties/{name}").then().statusCode(409);
        given().pathParam("name", AGENTS_UI).get("/registry/v2/admin/config/properties/{name}").then()
                .statusCode(200).body("value", equalTo("false"));
    }

    @Test
    void v2RejectsInvalidLogLevel() {
        given().contentType(CT_JSON).body("{\"value\":\"NOT_A_LEVEL\"}").pathParam("name", LOG_LEVEL)
                .put("/registry/v2/admin/config/properties/{name}").then().statusCode(400);
        given().contentType(CT_JSON).body("{\"value\":\"DEBUG\"}").pathParam("name", LOG_LEVEL)
                .put("/registry/v2/admin/config/properties/{name}").then().statusCode(204);
        given().pathParam("name", LOG_LEVEL).get("/registry/v2/admin/config/properties/{name}").then()
                .statusCode(200).body("value", equalTo("DEBUG"));
    }
}
