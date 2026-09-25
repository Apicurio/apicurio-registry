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

    private static final String READ_ONLY_PROPERTY_NAME = "apicurio.storage.read-only.enabled";

    @AfterEach
    public void resetStorageReadOnly() {
        setStorageReadOnly(false);
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

        setStorageReadOnly(true);

        given().when().contentType(CT_JSON).get("/registry/v3/system/uiConfig").then().statusCode(200)
                .body("features.readOnly", equalTo(true));
    }

    private void setStorageReadOnly(boolean readOnly) {
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue(String.valueOf(readOnly));
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", READ_ONLY_PROPERTY_NAME)
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204);
    }

}
