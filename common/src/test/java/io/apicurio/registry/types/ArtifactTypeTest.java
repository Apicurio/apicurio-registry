package io.apicurio.registry.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArtifactTypeTest {

    @Test
    void testValueUsesEnumName() {
        Assertions.assertEquals("AVRO", ArtifactType.AVRO.value());
        Assertions.assertEquals("ICEBERG_VIEW", ArtifactType.ICEBERG_VIEW.value());
    }

    @Test
    void testFromValueReturnsBuiltInForKnownType() {
        Assertions.assertEquals(ArtifactType.BuiltIn.AVRO, ArtifactType.fromValue("AVRO"));
        Assertions.assertEquals(ArtifactType.BuiltIn.ICEBERG_VIEW, ArtifactType.fromValue("ICEBERG_VIEW"));
    }

    @Test
    void testFromValueReturnsCustomForUnknownType() {
        Assertions.assertEquals(new ArtifactType.Custom("MODEL_SCHEMA"),
                ArtifactType.fromValue("MODEL_SCHEMA"));
    }

    @Test
    void testFromValueKeepsCaseSensitivityForBuiltIns() {
        Assertions.assertEquals(new ArtifactType.Custom("avro"), ArtifactType.fromValue("avro"));
    }

    @Test
    void testParseBuiltinCustomType() {
        Assertions.assertTrue(ArtifactType.parseBuiltin("MODEL_SCHEMA").isEmpty());
    }
}
