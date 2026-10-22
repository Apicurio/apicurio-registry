package io.apicurio.registry.utils.converter;

import org.apache.kafka.connect.data.SchemaAndValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExtJsonConverterTest {

    @Test
    public void testToConnectDataNullPayload() {
        ExtJsonConverter converter = new ExtJsonConverter();
        // No specific configuration needed as the null check happens before
        // schema resolution or other configured behavior.

        SchemaAndValue result = converter.toConnectData("test-topic", null);

        Assertions.assertSame(SchemaAndValue.NULL, result, "Converter should return SchemaAndValue.NULL for null input byte array");
    }
}
