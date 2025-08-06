package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.noprofile.ccompat.rest.CCompatTestConstants.CONFIG_NONE;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@Typed(CCompatCompatibilityConfigTest.class)
@TestProfile(ForwardCompatModeProfile.class)
public class CCompatCompatibilityConfigTest extends AbstractResourceTestBase {

    @NotNull
    public String getCcompatBasePath() {
        return "/ccompat/v7";
    }

    @NotNull
    public String getCoreBasePath() {
        return "/registry/v2";
    }

    CompatibilityLevelDto.Level getCompatibilityLevelFromCoreApi() {
        var respCore = given()
            .when()
            .contentType(ContentTypes.JSON)
            .get(getCoreBasePath() + "/admin/rules/COMPATIBILITY")
            .then()
            .statusCode(200)
            .extract().as(Rule.class);
        return CompatibilityLevelDto.Level.valueOf(respCore.getConfig());
    }

    CompatibilityLevelDto.Level getCompatibilityLevelFromCcompatApi() {
        var respCcompat = given()
            .when()
            .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
            .get(getCcompatBasePath() + "/config/")
            .then()
            .statusCode(200)
            .extract().as(CompatibilityLevelParamDto.class);
        return CompatibilityLevelDto.Level.valueOf(respCcompat.getCompatibilityLevel());
    }

    /**
     * Test that the default global compatibility level is the same, no matter if we use
     * the core or ccompat endpoint for retrieving its value.
     */
    @Test
    public void testConfigEndpoints() {
        // make sure that the default global "FORWARD" compatibility level is returned by both endpoints
        assertEquals(CompatibilityLevelDto.Level.FORWARD, getCompatibilityLevelFromCoreApi());
        assertEquals(getCompatibilityLevelFromCoreApi(), getCompatibilityLevelFromCcompatApi());

        // change the compatibility level and check that it is the same in both endpoints
        given()
            .when()
            .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
            .body(CONFIG_NONE)
            .put(getCcompatBasePath() + "/config")
            .then()
            .statusCode(200);

        assertEquals(CompatibilityLevelDto.Level.NONE, getCompatibilityLevelFromCoreApi());
        assertEquals(getCompatibilityLevelFromCoreApi(), getCompatibilityLevelFromCcompatApi());
    }

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) {
        // Do nothing... (we want to keep the default global rules)
    }
}
