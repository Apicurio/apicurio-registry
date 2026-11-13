package io.apicurio.registry.config;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.agents.noprofile.rest.aicatalog.AiCatalogEnabledProfile;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * With the experimental features gate on, an experimental dynamic property can be switched on at runtime.
 */
@QuarkusTest
@TestProfile(AiCatalogEnabledProfile.class)
public class ExperimentalDynamicConfigGateEnabledTest extends AbstractResourceTestBase {

    private static final String AGENTS_UI = "apicurio.ui.features.agents.enabled";

    @AfterEach
    void reset() {
        given().pathParam("name", AGENTS_UI).delete("/registry/v3/admin/config/properties/{name}");
    }

    @Test
    void experimentalPropertyCanBeEnabledAtRuntime() {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue("true");
        given().contentType(CT_JSON).body(update).pathParam("name", AGENTS_UI)
                .put("/registry/v3/admin/config/properties/{name}").then().statusCode(204);
        given().get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.agents", equalTo(true));
    }
}
