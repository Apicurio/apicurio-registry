package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
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
        setField("adminProperties", new Properties());
        setField("protocol", Optional.empty());
        setField("trustStoreLocation", Optional.empty());
        setField("trustStoreType", Optional.empty());
        setField("trustStorePassword", Optional.empty());
        setField("keyStoreLocation", Optional.empty());
        setField("keyStoreType", Optional.empty());
        setField("keyStorePassword", Optional.empty());
        setField("keyPassword", Optional.empty());
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

    @Test
    public void testSslConfigurationWithNewProperties() throws Exception {
        setField("trustStoreLocation", Optional.of("/path/to/truststore.jks"));
        setField("trustStoreType", Optional.of("JKS"));
        setField("trustStorePassword", Optional.of("trustpass"));

        setField("keyStoreLocation", Optional.of("/path/to/keystore.jks"));
        setField("keyStoreType", Optional.of("JKS"));
        setField("keyStorePassword", Optional.of("keypass"));
        setField("keyPassword", Optional.of("keypwd"));

        Map<String, String> consumerProps = configuration.getConsumerProperties();
        Map<String, String> adminProps = configuration.getAdminProperties();

        // Assert consumer properties
        Assertions.assertEquals("JKS", consumerProps.get(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
        Assertions.assertEquals("/path/to/truststore.jks", consumerProps.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
        Assertions.assertEquals("trustpass", consumerProps.get(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));

        Assertions.assertEquals("JKS", consumerProps.get(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG));
        Assertions.assertEquals("/path/to/keystore.jks", consumerProps.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
        Assertions.assertEquals("keypass", consumerProps.get(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));
        Assertions.assertEquals("keypwd", consumerProps.get(SslConfigs.SSL_KEY_PASSWORD_CONFIG));

        // Assert admin properties
        Assertions.assertEquals("JKS", adminProps.get(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
        Assertions.assertEquals("/path/to/truststore.jks", adminProps.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
        Assertions.assertEquals("trustpass", adminProps.get(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));

        Assertions.assertEquals("JKS", adminProps.get(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG));
        Assertions.assertEquals("/path/to/keystore.jks", adminProps.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
        Assertions.assertEquals("keypass", adminProps.get(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));
        Assertions.assertEquals("keypwd", adminProps.get(SslConfigs.SSL_KEY_PASSWORD_CONFIG));
    }

    @Test
    public void testDeprecatedSslPropertiesAreIgnored() throws Exception {
        // 1. Structure check: Assert that the deprecated fields no longer exist on the class
        Assertions.assertThrows(NoSuchFieldException.class, () -> {
            KafkaSqlConfiguration.class.getDeclaredField("trustStorePasswordDeprecated");
        });
        Assertions.assertThrows(NoSuchFieldException.class, () -> {
            KafkaSqlConfiguration.class.getDeclaredField("keyStoreLocationDeprecated");
        });
        Assertions.assertThrows(NoSuchFieldException.class, () -> {
            KafkaSqlConfiguration.class.getDeclaredField("keyStoreTypeDeprecated");
        });
        Assertions.assertThrows(NoSuchFieldException.class, () -> {
            KafkaSqlConfiguration.class.getDeclaredField("keyStorePasswordDeprecated");
        });
        Assertions.assertThrows(NoSuchFieldException.class, () -> {
            KafkaSqlConfiguration.class.getDeclaredField("keyPasswordDeprecated");
        });

        // 2. Behavior check: Assert that if deprecated properties are supplied, they do not map to any Kafka SSL config
        Properties deprecatedConsumerProps = new Properties();
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.truststore.location", "/path/to/truststore.jks");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.truststore.password", "trustpass");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.truststore.type", "JKS");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.keystore.location", "/path/to/keystore.jks");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.keystore.password", "keypass");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.keystore.type", "JKS");
        deprecatedConsumerProps.setProperty("apicurio.kafkasql.ssl.key.password", "keypwd");
        setField("consumerProperties", deprecatedConsumerProps);

        Properties deprecatedAdminProps = new Properties();
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.truststore.location", "/path/to/truststore.jks");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.truststore.password", "trustpass");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.truststore.type", "JKS");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.keystore.location", "/path/to/keystore.jks");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.keystore.password", "keypass");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.keystore.type", "JKS");
        deprecatedAdminProps.setProperty("apicurio.kafkasql.ssl.key.password", "keypwd");
        setField("adminProperties", deprecatedAdminProps);

        Map<String, String> consumerProps = configuration.getConsumerProperties();
        Map<String, String> adminProps = configuration.getAdminProperties();

        java.util.List<String> sslKeys = java.util.List.of(
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG
        );

        for (String key : sslKeys) {
            Assertions.assertNull(consumerProps.get(key), "Deprecated SSL property " + key + " should not be configured");
            Assertions.assertNull(adminProps.get(key), "Deprecated SSL property " + key + " should not be configured");
        }
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = KafkaSqlConfiguration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(configuration, value);
    }
}
