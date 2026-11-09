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
        write("custom.metadata.json", "{}");
        write("backup.2026.01.json", "{}");

        EntityReader reader = new EntityReader(importDir);

        assertInstanceOf(ManifestEntity.class, reader.readNextEntity());
        assertEquals(1L, readGlobalId(reader));
        assertEquals(4294967296L, readGlobalId(reader));
        assertNull(reader.readNextEntity());
    }

    @Test
    void nonEntityFilesWithMultipleDotsAndDottedSubdirectoriesAreSkipped() throws IOException {
        Path subDir = Files.createDirectories(importDir.resolve("v1.2.3.export"));
        Files.writeString(subDir.resolve("00000.Manifest.json"), "{\"systemVersion\":\"3.0.0\"}", StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("report.audit.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("config.test.json"), "{}", StandardCharsets.UTF_8);

        EntityReader reader = new EntityReader(importDir);

        assertInstanceOf(ManifestEntity.class, reader.readNextEntity());
        assertNull(reader.readNextEntity());
    }
}
