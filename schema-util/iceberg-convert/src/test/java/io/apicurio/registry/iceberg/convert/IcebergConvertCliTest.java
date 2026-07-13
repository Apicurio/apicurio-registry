package io.apicurio.registry.iceberg.convert;

import org.apache.avro.Schema;
import org.apache.commons.io.IOUtils;
import org.apache.iceberg.SchemaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IcebergConvertCliTest {

    private static void copyResource(String path, Path target) throws IOException {
        try (InputStream in = IcebergConvertCliTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Missing test resource: " + path);
            Files.writeString(target, IOUtils.toString(in, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
    }

    @Test
    void avroToIcebergWritesOutputFile(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.avsc");
        Path output = dir.resolve("out.json");
        copyResource("avro/nested.avsc", input);

        IcebergConvertCli.main(new String[] { "avro2iceberg", input.toString(), output.toString() });

        String result = Files.readString(output, StandardCharsets.UTF_8);
        assertNotNull(SchemaParser.fromJson(result).findField("id"));
    }

    @Test
    void icebergToAvroWritesToStdout(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.json");
        copyResource("iceberg/simple.iceberg.json", input);

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            IcebergConvertCli.main(new String[] { "iceberg2avro", input.toString() });
        } finally {
            System.setOut(original);
        }

        Schema avro = new Schema.Parser().parse(captured.toString(StandardCharsets.UTF_8));
        assertEquals(Schema.Type.RECORD, avro.getType());
    }
}
