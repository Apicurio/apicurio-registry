package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
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
        return "/registry/v3";
    }

    CompatibilityLevel getCompatibilityLevelFromCoreApi() {
        String level = clientV3.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).get().getConfig();
        return CompatibilityLevel.valueOf(level);
    }

    CompatibilityLevel getCompatibilityLevelFromCcompatApi() {
        String compatibilityLevel = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getCcompatBasePath() + "/config/")
                .then()
                .statusCode(200)
                .extract().path("compatibilityLevel");
        return CompatibilityLevel.valueOf(compatibilityLevel);
    }

    /**
     * Test that the default global compatibility level is the same, no matter if we use
     * the core or ccompat endpoint for retrieving its value.
     */
    @Test
    public void testConfigEndpoints() {
        // make sure that the default global "FORWARD" compatibility level is returned by both endpoints
        assertEquals(CompatibilityLevel.FORWARD, getCompatibilityLevelFromCoreApi());
        assertEquals(getCompatibilityLevelFromCoreApi(), getCompatibilityLevelFromCcompatApi());

        // change the compatibility level and check that it is the same in both endpoints
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_NONE)
                .put(getCcompatBasePath() + "/config")
                .then()
                .statusCode(200);

        assertEquals(CompatibilityLevel.NONE, getCompatibilityLevelFromCoreApi());
        assertEquals(getCompatibilityLevelFromCoreApi(), getCompatibilityLevelFromCcompatApi());
    }

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
    }
}