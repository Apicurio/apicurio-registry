package io.apicurio.registry.content.refs.resolve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaSourceSetTest {

    @TempDir
    Path tmp;

    @Test
    void directoriesProduceRootRelativeLocations() throws IOException {
        Files.createDirectories(tmp.resolve("common"));
        Files.writeString(tmp.resolve("common/Address.avsc"), "{}");
        Files.writeString(tmp.resolve("Order.avsc"), "{}");
        Files.writeString(tmp.resolve("notes.txt"), "x");

        SchemaSourceSet set = SchemaSourceSet.fromDirectories(List.of(tmp), SchemaSourceSet.DEFAULT_FILTER);

        assertEquals(List.of("Order.avsc", "common/Address.avsc"),
                set.sources().stream().map(SchemaSource::location).sorted().toList());
        assertEquals("application/json",
                set.byLocation("common/Address.avsc").orElseThrow().content().getContentType());
    }

    @Test
    void nestedRootsYieldOneSourceWithAliases() throws IOException {
        Files.createDirectories(tmp.resolve("sample"));
        Files.writeString(tmp.resolve("sample/a.proto"), "syntax = \"proto3\";");

        SchemaSourceSet set = SchemaSourceSet.fromDirectories(List.of(tmp.resolve("sample"), tmp),
                SchemaSourceSet.DEFAULT_FILTER);

        assertEquals(1, set.sources().size());
        assertEquals(List.of("a.proto", "sample/a.proto"), set.sources().get(0).aliases());
    }

    @Test
    void normalizeStripsDotSegmentsAndBackslashes() {
        assertEquals("common/foo.json", SchemaSourceSet.normalize("./common/../common\\foo.json"));
    }
}
