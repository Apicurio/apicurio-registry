package io.apicurio.registry.content.refs.resolve;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSchemaSourceTest {

    @ParameterizedTest
    @CsvSource({
        "a.yaml, application/x-yaml",
        "a.yml, application/x-yaml",
        "a.proto, application/x-protobuf",
        "a.graphql, application/graphql",
        "a.xml, application/xml",
        "a.xsd, application/xml",
        "a.wsdl, application/xml",
        "a.avsc, application/json",
        "a.json, application/json",
        "A.YAML, application/x-yaml"
    })
    void contentTypeByExtension(String fileName, String expected) {
        assertEquals(expected, FileSchemaSource.contentTypeFor(Path.of(fileName)));
    }
}
