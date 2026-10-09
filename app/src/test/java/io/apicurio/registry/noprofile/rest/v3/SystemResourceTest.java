package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class SystemResourceTest extends AbstractResourceTestBase {

    private static final String STORAGE_READ_ONLY_PROPERTY_NAME = "apicurio.storage.read-only.enabled";
    private static final String UI_READ_ONLY_PROPERTY_NAME = "apicurio.ui.features.read-only.enabled";
    private static final String DELETE_GROUP_PROPERTY_NAME = "apicurio.rest.deletion.group.enabled";
    private static final String DELETE_ARTIFACT_PROPERTY_NAME = "apicurio.rest.deletion.artifact.enabled";
    private static final String DELETE_VERSION_PROPERTY_NAME = "apicurio.rest.deletion.artifact-version.enabled";

    @AfterEach
    public void resetReadOnlyProperties() {
        updateConfigProperty(STORAGE_READ_ONLY_PROPERTY_NAME, false);
        updateConfigProperty(UI_READ_ONLY_PROPERTY_NAME, false);
        updateConfigProperty(DELETE_GROUP_PROPERTY_NAME, false);
        updateConfigProperty(DELETE_ARTIFACT_PROPERTY_NAME, false);
        updateConfigProperty(DELETE_VERSION_PROPERTY_NAME, false);
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

    // Runs twice: once flipping storage read-only, once flipping UI read-only -- the delete
    // flags must be suppressed by either source, since readOnly is the OR of both.
    @ParameterizedTest
    @ValueSource(strings = { STORAGE_READ_ONLY_PROPERTY_NAME, UI_READ_ONLY_PROPERTY_NAME })
    public void testDeleteFlagsReflectReadOnlyState(String readOnlyPropertyName) {
        // Enable all three deletion features — they default to false, so there's nothing to
        // prove without turning them on first.
        updateConfigProperty(DELETE_GROUP_PROPERTY_NAME, true);
        updateConfigProperty(DELETE_ARTIFACT_PROPERTY_NAME, true);
        updateConfigProperty(DELETE_VERSION_PROPERTY_NAME, true);

        // Baseline: deletion enabled, not read-only -> all delete flags should be true.
        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(false))
                .body("features.deleteGroup", equalTo(true))
                .body("features.deleteArtifact", equalTo(true))
                .body("features.deleteVersion", equalTo(true));

        updateConfigProperty(readOnlyPropertyName, true);

        // The delete flags must now report false, even though their own underlying feature
        // flags are still individually enabled -- the server, not the frontend, is responsible
        // for not advertising a write action that is guaranteed to fail.
        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(true))
                .body("features.deleteGroup", equalTo(false))
                .body("features.deleteArtifact", equalTo(false))
                .body("features.deleteVersion", equalTo(false));
    }

    private void updateConfigProperty(String propertyName, boolean value) {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue(String.valueOf(value));
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", propertyName)
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204);
    }

}
