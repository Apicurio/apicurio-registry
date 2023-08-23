package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.content.ContentHandle;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConfluentTestUtils {

    private static final String IoTmpDir = System.getProperty("java.io.tmpdir");
    private static final Random random = new Random();

    /**
     * Create a temporary directory
     */
    public static File tempDir(String namePrefix) {
        final File f = new File(IoTmpDir, namePrefix + "-" + random.nextInt(1000000));
        f.mkdirs();
        f.deleteOnExit();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                rm(f);
            }
        });
        return f;
    }

    /**
     * Recursively deleteSchemaVersion the given file/directory and any subfiles (if any exist)
     *
     * @param file The root file at which to begin deleting
     */
    public static void rm(File file) {
        if (file == null) {
            return;
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    rm(f);
                }
            }
        } else {
            file.delete();
        }
    }

    /**
     * Wait until a callable returns true or the timeout is reached.
     */
    public static void waitUntilTrue(Callable<Boolean> callable, long timeoutMs, String errorMsg) {
        try {
            long startTime = System.currentTimeMillis();
            Boolean state = false;
            do {
                state = callable.call();
                if (System.currentTimeMillis() > startTime + timeoutMs) {
                    fail(errorMsg);
                }
                Thread.sleep(50);
            } while (!state);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    /**
     * Helper method which checks the number of versions registered under the given subject.
     */
    public static void checkNumberOfVersions(RestService restService, int expected, String subject) throws IOException, RestClientException {
        List<Integer> versions = restService.getAllVersions(subject);
        assertEquals("Expected " + expected + " registered versions under subject " + subject + ", but found " + versions.size(), expected, versions.size());
    }

    /**
     * Register a new schema and verify that it can be found on the expected version.
     */
    public static void registerAndVerifySchema(RestService restService, String schemaString, String subject) throws IOException, RestClientException {
        int registeredId = restService.registerSchema(schemaString, subject);

        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", schemaString, restService.getId(registeredId).getSchemaString());
    }

    public static void registerAndVerifySchema(RestService restService, String schemaString, List<SchemaReference> references, String subject) throws IOException, RestClientException {
        int registeredId = restService.registerSchema(schemaString, AvroSchema.TYPE, references, subject);

        // the newly registered schema should be immediately readable on the leader
        assertEquals("Registered schema should be found", schemaString, restService.getId(registeredId).getSchemaString());
    }

    public static List<String> getRandomCanonicalAvroString(int num) {
        List<String> avroStrings = new ArrayList<String>();

        for (int i = 0; i < num; i++) {
            String schemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + random.nextInt(Integer.MAX_VALUE) + "\"}]}";
            avroStrings.add(new AvroSchema(ContentHandle.create(schemaString).content()).canonicalString());
        }
        return avroStrings;
    }

    public static List<String> getAvroSchemaWithReferences() {
        List<String> schemas = new ArrayList<>();
        String reference = "{\"type\":\"record\"," + "\"name\":\"Subrecord\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field2\",\"type\":\"string\"}]}";
        schemas.add(reference);
        String schemaString = "{\"type\":\"record\"," + "\"name\":\"MyRecord\"," + "\"namespace\":\"ns\"," + "\"fields\":" + "[{\"name\":\"field1\",\"type\":\"otherns.Subrecord\"}]}";
        schemas.add(schemaString);
        return schemas;
    }

    public static List<String> getRandomJsonSchemas(int num) {
        List<String> schemas = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            String schema = "{\"type\":\"object\",\"properties\":{\"f"
                    + random.nextInt(Integer.MAX_VALUE)
                    + "\":"
                    + "{\"type\":\"string\"}},\"additionalProperties\":false}";
            schemas.add(schema);
        }
        return schemas;
    }

    public static List<String> getRandomProtobufSchemas(int num) {
        List<String> schemas = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            String schema =
                    "syntax = \"proto3\";\npackage io.confluent.kafka.serializers.protobuf.test;\n\n"
                            + "message MyMessage {\n  string f"
                            + random.nextInt(Integer.MAX_VALUE)
                            + " = 1;\n  bool is_active = 2;\n}\n";
            schemas.add(schema);
        }
        return schemas;
    }

    public static String getBadSchema() {
        String schemaString = "{\"type\":\"bad-record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + random.nextInt(Integer.MAX_VALUE) + "\"}]}";
        return schemaString;
    }
}
