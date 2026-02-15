package io.apicurio.registry.iceberg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IcebergSchemaConverterTest {
    
    @Test
    public void testConverterExists() {
        IcebergSchemaConverter converter = new IcebergSchemaConverter();
        assertNotNull(converter);
    }
}
