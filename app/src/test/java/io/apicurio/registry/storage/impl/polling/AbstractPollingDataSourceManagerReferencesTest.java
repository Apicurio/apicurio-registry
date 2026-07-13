package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.polling.model.v0.Content;
import io.apicurio.registry.storage.impl.polling.model.v0.ContentReference;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ensures polling storage derives hashes from content-metadata references the same way SQL does (#8540).
 */
public class AbstractPollingDataSourceManagerReferencesTest {

    private static final String AVRO = "{\"type\":\"record\",\"name\":\"TestRecord\","
            + "\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}";

    @Test
    void extractReferencesReturnsNullWhenMetadataMissing() {
        assertNull(AbstractPollingDataSourceManager.extractReferences(null));
        assertNull(AbstractPollingDataSourceManager.extractReferences(Content.builder().build()));
    }

    @Test
    void extractReferencesMapsContentMetadata() {
        Content metadata = Content.builder()
                .references(List.of(ContentReference.builder()
                        .groupId("com.example")
                        .artifactId("Address")
                        .version("1")
                        .name("com.example.Address")
                        .build()))
                .build();

        List<ArtifactReferenceDto> refs = AbstractPollingDataSourceManager.extractReferences(metadata);
        assertEquals(1, refs.size());
        assertEquals("com.example", refs.get(0).getGroupId());
        assertEquals("Address", refs.get(0).getArtifactId());
        assertEquals("1", refs.get(0).getVersion());
        assertEquals("com.example.Address", refs.get(0).getName());
    }

    @Test
    void pollingContentHashMatchesSqlWhenReferencesAreIncluded() {
        RegistryStorageContentUtils utils = new RegistryStorageContentUtils();
        TypedContent content = TypedContent.create(AVRO, ContentTypes.APPLICATION_JSON);

        Content metadata = Content.builder()
                .references(List.of(ContentReference.builder()
                        .groupId("com.example")
                        .artifactId("Address")
                        .version("1")
                        .name("com.example.Address")
                        .build()))
                .build();

        List<ArtifactReferenceDto> refs = AbstractPollingDataSourceManager.extractReferences(metadata);

        String sqlStyle = utils.getContentHash(content, refs);
        String oldPollingStyle = utils.getContentHash(content, null);

        assertNotEquals(sqlStyle, oldPollingStyle,
                "pre-fix polling hashed without references and diverged from SQL");
        assertEquals(sqlStyle, utils.getContentHash(content, refs),
                "polling must hash with extracted references to match SQL");
    }
}
