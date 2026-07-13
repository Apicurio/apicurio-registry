package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression coverage for hash behavior that SQL and polling storages must share.
 * See #8540 — polling previously hashed as if references were always absent.
 */
public class RegistryStorageContentUtilsTest {

    private static final String AVRO = "{\"type\":\"record\",\"name\":\"TestRecord\","
            + "\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}";

    private final RegistryStorageContentUtils utils = new RegistryStorageContentUtils();

    private static List<ArtifactReferenceDto> oneReference() {
        return List.of(ArtifactReferenceDto.builder()
                .groupId("com.example")
                .artifactId("Address")
                .version("1")
                .name("com.example.Address")
                .build());
    }

    private static TypedContent avroContent() {
        return TypedContent.create(AVRO, ContentTypes.APPLICATION_JSON);
    }

    @Test
    void contentHashIncludesReferences() {
        TypedContent content = avroContent();

        String withRefs = utils.getContentHash(content, oneReference());
        String withoutRefs = utils.getContentHash(content, null);

        assertNotEquals(withRefs, withoutRefs,
                "contentHash must include serialized references when present");
    }

    @Test
    void contentHashIsStableForSameContentAndReferences() {
        TypedContent content = avroContent();
        List<ArtifactReferenceDto> refs = oneReference();

        assertEquals(utils.getContentHash(content, refs), utils.getContentHash(content, refs));
    }
}
