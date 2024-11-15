package apicurio.common.app.components.config.index.it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ConfigIndexResourceTest {

    @Test
    public void testGetDynamicConfigInfo() {
        ConfigProps allProperties = given().when().get("/config/all").as(ConfigProps.class);
        ConfigProps acceptedProperties = given().when().get("/config/accepted").as(ConfigProps.class);

        Assertions.assertTrue(allProperties.getProperties().size() == 5);
        Assertions.assertTrue(acceptedProperties.getProperties().size() == 4);
        Assertions.assertNotNull(allProperties.getProperty("apicurio.properties.dynamic.long"));
        Assertions.assertEquals("apicurio.properties.dynamic.long",
                allProperties.getProperty("apicurio.properties.dynamic.long").getName());
        Assertions.assertEquals("17",
                allProperties.getProperty("apicurio.properties.dynamic.long").getValue());
        Assertions.assertTrue(allProperties.hasProperty("apicurio.properties.dynamic.bool.dep"));
        Assertions.assertFalse(acceptedProperties.hasProperty("apicurio.properties.dynamic.bool.dep"));
        Assertions.assertFalse(acceptedProperties.hasProperty("property.does.not.exist"));

        ConfigProp booleanProp = given().when().get("/config/all/apicurio.properties.dynamic.bool")
                .as(ConfigProp.class);
        Assertions.assertEquals("false", booleanProp.getValue());

        // Set the bool property to true
        given().when().get("/config/update");

        // Value should be true now
        booleanProp = given().when().get("/config/all/apicurio.properties.dynamic.bool").as(ConfigProp.class);
        Assertions.assertEquals("true", booleanProp.getValue());

        allProperties = given().when().get("/config/all").as(ConfigProps.class);
        Assertions.assertTrue(allProperties.hasProperty("apicurio.properties.dynamic.bool"));
        Assertions.assertEquals("true", allProperties.getPropertyValue("apicurio.properties.dynamic.bool"));
        // Accepted properties should now have 5 items
        acceptedProperties = given().when().get("/config/accepted").as(ConfigProps.class);
        Assertions.assertTrue(acceptedProperties.getProperties().size() == 5);
        Assertions.assertTrue(acceptedProperties.hasProperty("apicurio.properties.dynamic.bool"));
        Assertions.assertEquals("true",
                acceptedProperties.getPropertyValue("apicurio.properties.dynamic.bool"));
        Assertions.assertTrue(acceptedProperties.hasProperty("apicurio.properties.dynamic.bool.dep"));
        Assertions.assertFalse(acceptedProperties.hasProperty("property.does.not.exist"));
    }
}
