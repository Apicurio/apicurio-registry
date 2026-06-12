package io.apicurio.registry.xsd.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.xml.validation.SchemaFactory;

/**
 * Tests for {@link SchemaFactoryAccessor}.
 */
public class SchemaFactoryAccessorTest {

    @Test
    void testReturnsNonNullFactory() {
        SchemaFactory factory = SchemaFactoryAccessor.getSchemaFactory();
        Assertions.assertNotNull(factory);
    }

    @Test
    void testFactoryHasSecureProcessingEnabled() throws Exception {
        SchemaFactory factory = SchemaFactoryAccessor.getSchemaFactory();
        boolean secureProcessing = factory
                .getFeature("http://javax.xml.XMLConstants/feature/secure-processing");
        Assertions.assertTrue(secureProcessing);
    }

}
