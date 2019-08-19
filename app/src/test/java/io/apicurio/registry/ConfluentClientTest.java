package io.apicurio.registry;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

@QuarkusTest
public class ConfluentClientTest {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081/confluent", 3);
    }

    @Test
    public void testSmoke() throws Exception {
        SchemaRegistryClient client = buildClient();
        Collection<String> subjects = client.getAllSubjects();
        Assertions.assertNotNull(subjects);
    }


    @Test
    public void testSimpleOps() throws Exception {
        SchemaRegistryClient client = buildClient();
        final String subject = "foobar";

        Schema schema1 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id1 = client.register(subject, schema1);

        Schema schema2 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"f2\",\"type\":\"string\"}]}");
        int id2 = client.register(subject, schema2);

        Schema schema = client.getById(id1);
        Assertions.assertNotNull(schema);

        client.reset();

        // TODO -- it's always false atm!
        Assertions.assertFalse(client.testCompatibility(subject, schema2));

        schema = client.getById(id2);
        Assertions.assertNotNull(schema);

        Collection<String> subjects = client.getAllSubjects();
        Assertions.assertTrue(subjects.contains(subject));

        List<Integer> versions = client.getAllVersions(subject);
        Assertions.assertTrue(versions.contains(1));
        Assertions.assertTrue(versions.contains(2));

        // TODO -- match per schema!
        //int v1 = client.getVersion(subject, schema1);
        //Assertions.assertEquals(1, v1);

        int v2 = client.getVersion(subject, schema2);
        Assertions.assertEquals(2, v2);

        int d1 = client.deleteSchemaVersion(subject, "1");
        Assertions.assertEquals(1, d1);
        int d2 = client.deleteSchemaVersion(subject, "2");
        Assertions.assertEquals(2, d2);
        //int dl = client.deleteSchemaVersion(subject, "latest");
        //Assertions.assertEquals(2, dl);

        // TODO: discuss with Ales: both versions of the schema were deleted above.  should the subject be deleted when all versions are deleted?
//        versions = client.deleteSubject(subject);
        // TODO: why would this work?  deleting the subject would return the already-deleted versions?
//        Assertions.assertTrue(versions.contains(1));
//        Assertions.assertTrue(versions.contains(2));
    }

    // TODO -- cover all endpoints!
}
