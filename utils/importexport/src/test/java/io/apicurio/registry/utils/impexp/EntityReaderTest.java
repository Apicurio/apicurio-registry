package io.apicurio.registry.utils.impexp;

import io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityReaderTest {

    @TempDir
    Path importDir;

    private void write(String name, String json) throws IOException {
        Files.writeString(importDir.resolve(name), json, StandardCharsets.UTF_8);
    }

    private long readGlobalId(EntityReader reader) throws IOException {
        Entity entity = reader.readNextEntity();
        assertInstanceOf(ArtifactVersionEntity.class, entity);
        return ((ArtifactVersionEntity) entity).globalId;
    }

    @Test
    void v2VersionsAreSortedByGlobalIdWithoutOverflowAndNonEntityFilesAreSkipped() throws IOException {
        write("export.Manifest.json", "{\"systemVersion\":\"2.6.0\"}");
        // The difference of these ids does not fit in an int; (int) (a - b) yields the wrong sign.
        write("a.ArtifactVersion.json", "{\"globalId\":4294967296}");
        write("b.ArtifactVersion.json", "{\"globalId\":1}");
        write("readme.json", "{}");

        EntityReader reader = new EntityReader(importDir);

        assertInstanceOf(ManifestEntity.class, reader.readNextEntity());
        assertEquals(1L, readGlobalId(reader));
        assertEquals(4294967296L, readGlobalId(reader));
        assertNull(reader.readNextEntity());
    }
}
