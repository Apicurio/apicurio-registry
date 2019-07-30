package io.apicurio.registry;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

@QuarkusTest
public class ConfluentClientTest {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081", 3);
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
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id = client.register("foobar", schema);

        schema = client.getById(id);
        Assertions.assertNotNull(schema);
    }

}
