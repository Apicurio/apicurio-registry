package io.apicurio.registry.noprofile.ccompat;

import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Properties;

@QuarkusTest
public class ConfluentSerdeTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.http.test-port")
    int testPort;

    @SuppressWarnings({ "rawtypes", "unchecked", "resource" })
    @Test
    public void testProtobufSchemaWithReferences() {

        CreateRule rule = new CreateRule();
        rule.setConfig("SYNTAX_ONLY");
        rule.setRuleType(RuleType.VALIDITY);

        clientV3.admin().rules().post(rule);
        Properties properties = new Properties();
        String serverUrl = "http://localhost:%s/apis/ccompat/v7";
        properties.setProperty(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                String.format(serverUrl, testPort));
        properties.setProperty(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        KafkaProtobufSerializer kafkaProtobufSerializer = new KafkaProtobufSerializer();
        kafkaProtobufSerializer.configure(properties, false);

        byte[] data = kafkaProtobufSerializer.serialize("test", TableNotification.newBuilder().build());

        KafkaProtobufDeserializer protobufKafkaDeserializer = new KafkaProtobufDeserializer();
        protobufKafkaDeserializer.configure(properties, false);

        protobufKafkaDeserializer.deserialize("test", data);
    }
}
