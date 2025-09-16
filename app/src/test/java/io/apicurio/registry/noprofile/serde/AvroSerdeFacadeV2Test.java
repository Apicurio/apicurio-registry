package io.apicurio.registry.noprofile.serde;

import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
public class AvroSerdeFacadeV2Test extends AbstractClientFacadeTestBase {

    @Test
    public void testConfiguration() throws Exception {
        String recordName = "myrecord3";
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName
                + "\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();

        /* final Integer globalId = */
        createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, schema.toString(),
                ContentTypes.APPLICATION_JSON);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_VERSION, "1");
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>();
        serializer.configure(config, true);

        Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<GenericData.Record>();

        TestUtils.retry(() -> {

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");
            byte[] bytes = serializer.serialize(topic, record);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl(testPort));
            deserializer.configure(deserializerConfig, true);

            GenericData.Record deserializedRecord = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals(record, deserializedRecord);
            Assertions.assertEquals("somebar", record.get("bar").toString());

            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);

            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());

            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);
            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());

        });

        serializer.close();
        deserializer.close();
    }
}
