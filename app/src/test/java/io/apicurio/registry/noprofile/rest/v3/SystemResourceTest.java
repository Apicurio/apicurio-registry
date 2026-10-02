package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class SystemResourceTest extends AbstractResourceTestBase {

    private static final String STORAGE_READ_ONLY_PROPERTY_NAME = "apicurio.storage.read-only.enabled";
    private static final String UI_READ_ONLY_PROPERTY_NAME = "apicurio.ui.features.read-only.enabled";

    @AfterEach
    public void resetReadOnlyProperties() {
        updateConfigProperty(STORAGE_READ_ONLY_PROPERTY_NAME, false);
        updateConfigProperty(UI_READ_ONLY_PROPERTY_NAME, false);
    }

    @Test
    public void testSystemInformation() {
        given().when().contentType(CT_JSON).get("/registry/v3/system/info").then().statusCode(200)
                .body("name", equalTo("Apicurio Registry (SQL)"))
                .body("description",
                        equalTo("High performance, runtime registry for schemas and API designs."))
                .body("version", notNullValue()).body("builtOn", notNullValue());
    }

    @Test
    public void testUiConfigReadOnlyReflectsStorageReadOnly() {
        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(false));

        updateConfigProperty(STORAGE_READ_ONLY_PROPERTY_NAME, true);

        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(true));
    }

    @Test
    public void testUiReadOnlyFeatureFlagIsDynamic() {
        // The property must be recognized by the admin config API (not 404), proving it is
        // genuinely wired as a @Dynamic property rather than just claiming to be one.
        given().when().pathParam("propertyName", UI_READ_ONLY_PROPERTY_NAME)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .body("value", equalTo("false"));

        updateConfigProperty(UI_READ_ONLY_PROPERTY_NAME, true);

        // Toggling it must take effect immediately, with no server restart.
        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(true));
    }

    private void updateConfigProperty(String propertyName, boolean value) {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue(String.valueOf(value));
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", propertyName)
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204);
    }

}
