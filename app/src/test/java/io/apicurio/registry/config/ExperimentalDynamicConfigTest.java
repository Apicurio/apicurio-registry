package io.apicurio.registry.config;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * With the experimental features gate off (the default), an experimental property that is also dynamic
 * cannot be switched on at runtime, and a value stored while the gate was on is ignored: the startup
 * check would otherwise be bypassed.
 */
@QuarkusTest
public class ExperimentalDynamicConfigTest extends AbstractResourceTestBase {

    private static final String AGENTS_UI = "apicurio.ui.features.agents.enabled";

    @Inject
    @Current
    RegistryStorage storage;

    @AfterEach
    void reset() {
        given().pathParam("name", AGENTS_UI).delete("/registry/v3/admin/config/properties/{name}");
    }

    private static UpdateConfigurationProperty value(String value) {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue(value);
        return update;
    }

    @Test
    void experimentalPropertyCannotBeEnabledAtRuntime() {
        given().contentType(CT_JSON).body(value("true")).pathParam("name", AGENTS_UI)
                .put("/registry/v3/admin/config/properties/{name}").then().statusCode(409);
        given().pathParam("name", AGENTS_UI).get("/registry/v3/admin/config/properties/{name}").then()
                .statusCode(200).body("value", equalTo("false"));
        given().get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.agents", equalTo(false));
    }

    @Test
    void valueStoredWhileTheGateWasOnIsIgnored() {
        // As left behind by an earlier run with the gate enabled
        storage.setConfigProperty(new DynamicConfigPropertyDto(AGENTS_UI, "true"));
        given().get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.agents", equalTo(false));
    }

    @Test
    void experimentalPropertyCanStillBeSetToFalse() {
        given().contentType(CT_JSON).body(value("false")).pathParam("name", AGENTS_UI)
                .put("/registry/v3/admin/config/properties/{name}").then().statusCode(204);
    }
}
