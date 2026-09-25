package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class KafkaSqlConfigurationTest {

    private KafkaSqlConfiguration configuration;

    @BeforeEach
    public void setUp() throws Exception {
        configuration = new KafkaSqlConfiguration();
        setField("groupPrefix", "apicurio-");
        setField("bootstrapServers", "localhost:9092");
        setField("consumerProperties", new Properties());
        setField("protocol", Optional.empty());
        setField("trustStoreLocation", Optional.empty());
        setField("trustStoreType", Optional.empty());
        setField("trustStorePassword", Optional.empty());
        setField("trustStorePasswordDeprecated", Optional.empty());
        setField("keyStoreLocation", Optional.empty());
        setField("keyStoreLocationDeprecated", Optional.empty());
        setField("keyStoreType", Optional.empty());
        setField("keyStoreTypeDeprecated", Optional.empty());
        setField("keyStorePassword", Optional.empty());
        setField("keyStorePasswordDeprecated", Optional.empty());
        setField("keyPassword", Optional.empty());
        setField("keyPasswordDeprecated", Optional.empty());
    }

    @Test
    public void testConsumerGroupIdIsAlwaysRandom() throws Exception {
        Map<String, String> props1 = configuration.getConsumerProperties();
        Map<String, String> props2 = configuration.getConsumerProperties();

        String groupId1 = props1.get(ConsumerConfig.GROUP_ID_CONFIG);
        String groupId2 = props2.get(ConsumerConfig.GROUP_ID_CONFIG);

        Assertions.assertNotNull(groupId1);
        Assertions.assertNotNull(groupId2);
        Assertions.assertTrue(groupId1.startsWith("apicurio-"));
        Assertions.assertTrue(groupId2.startsWith("apicurio-"));
        Assertions.assertNotEquals(groupId1, groupId2,
                "Each call must generate a unique consumer group ID");
    }

    @Test
    public void testUserProvidedGroupIdIsOverridden() throws Exception {
        Properties propsWithGroupId = new Properties();
        propsWithGroupId.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "my-fixed-group");
        setField("consumerProperties", propsWithGroupId);

        Map<String, String> props = configuration.getConsumerProperties();
        String groupId = props.get(ConsumerConfig.GROUP_ID_CONFIG);

        Assertions.assertNotNull(groupId);
        Assertions.assertNotEquals("my-fixed-group", groupId,
                "User-provided group.id must be overridden with a random UUID");
        Assertions.assertTrue(groupId.startsWith("apicurio-"));
    }

    @Test
    public void testGroupPrefixIsUsed() throws Exception {
        setField("groupPrefix", "custom-prefix-");

        Map<String, String> props = configuration.getConsumerProperties();
        String groupId = props.get(ConsumerConfig.GROUP_ID_CONFIG);

        Assertions.assertNotNull(groupId);
        Assertions.assertTrue(groupId.startsWith("custom-prefix-"),
                "Consumer group ID must use the configured prefix");
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = KafkaSqlConfiguration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(configuration, value);
    }
}
