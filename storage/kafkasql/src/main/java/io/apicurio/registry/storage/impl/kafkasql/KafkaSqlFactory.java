/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.kafkasql;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlPartitioner;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.utils.RegistryProperties;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class KafkaSqlFactory {

    @Inject
    @ConfigProperty(name = "registry.kafkasql.bootstrap.servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.topic", defaultValue = "kafkasql-journal")
    String topic;

    @Inject
    @RegistryProperties(value = "registry.kafkasql.topic")
    Properties topicProperties;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.topic.auto-create", defaultValue = "true")
    Boolean topicAutoCreate;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.startupLag", defaultValue = "1000")
    Integer startupLag;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.poll.timeout", defaultValue = "1000")
    Integer pollTimeout;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.coordinator.response-timeout", defaultValue = "30000")
    Integer responseTimeout;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.producer"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties producerProperties;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.consumer"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties consumerProperties;

    @Inject
    @RegistryProperties(
            value = {"registry.kafka.common", "registry.kafkasql.admin"},
            empties = {"ssl.endpoint.identification.algorithm="}
    )
    Properties adminProperties;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.enabled", defaultValue = "false")
    boolean securityEnabled;

    @ConfigProperty(name = "registry.kafkasql.security.protocol", defaultValue = "")
    String protocol;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.mechanism", defaultValue = "")
    String saslMechanism;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.client-secret", defaultValue = "")
    String clientSecret;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.token.endpoint", defaultValue = "")
    String tokenEndpoint;

    @ConfigProperty(name = "registry.kafkasql.security.sasl.login.callback.handler.class", defaultValue = "")
    String loginCallbackHandler;

    @ConfigProperty(name = "registry.kafkasql.security.ssl.truststore.location")
    Optional<String> trustStoreLocation;

    @ConfigProperty(name = "registry.kafkasql.security.ssl.truststore.type")
    Optional<String> trustStoreType;

    @ConfigProperty(name = "registry.kafkasql.ssl.truststore.password")
    Optional<String> trustStorePassword;

    @ConfigProperty(name = "registry.kafkasql.ssl.keystore.location")
    Optional<String> keyStoreLocation;

    @ConfigProperty(name = "registry.kafkasql.ssl.keystore.type")
    Optional<String> keyStoreType;

    @ConfigProperty(name = "registry.kafkasql.ssl.keystore.password")
    Optional<String> keyStorePassword;

    @ConfigProperty(name = "registry.kafkasql.ssl.key.password")
    Optional<String> keyPassword;

    @ApplicationScoped
    @Produces
    public KafkaSqlConfiguration createConfiguration() {
        KafkaSqlConfiguration config = new KafkaSqlConfiguration() {
            @Override
            public String bootstrapServers() {
                return bootstrapServers;
            }
            @Override
            public String topic() {
                return topic;
            }
            @Override
            public Properties topicProperties() {
                return topicProperties;
            }
            @Override
            public boolean isTopicAutoCreate() {
                return topicAutoCreate;
            }
            @Override
            public Integer startupLag() {
                return startupLag;
            }
            @Override
            public Integer pollTimeout() {
                return pollTimeout;
            }
            @Override
            public Integer responseTimeout() {
                return responseTimeout;
            }
            @Override
            public Properties producerProperties() {
                return producerProperties;
            }
            @Override
            public Properties consumerProperties() {
                return consumerProperties;
            }
            @Override
            public Properties adminProperties() {
                tryToConfigureSecurity(adminProperties);
                return adminProperties;
            }

        };
        return config;
    }

    /**
     * Creates the Kafka producer.
     */
    @ApplicationScoped
    @Produces
    public ProducerActions<MessageKey, MessageValue> createKafkaProducer() {
        Properties props = (Properties) producerProperties.clone();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + UUID.randomUUID().toString());
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class);

        tryToConfigureSecurity(props);

        // Create the Kafka producer
        KafkaSqlKeySerializer keySerializer = new KafkaSqlKeySerializer();
        KafkaSqlValueSerializer valueSerializer = new KafkaSqlValueSerializer();
        return new AsyncProducer<MessageKey, MessageValue>(props, keySerializer, valueSerializer);
    }

    /**
     * Creates the Kafka consumer.
     */
    @ApplicationScoped
    @Produces
    public KafkaConsumer<MessageKey, MessageValue> createKafkaConsumer() {
        Properties props = (Properties) consumerProperties.clone();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        tryToConfigureSecurity(props);

        // Create the Kafka Consumer
        KafkaSqlKeyDeserializer keyDeserializer = new KafkaSqlKeyDeserializer();
        KafkaSqlValueDeserializer valueDeserializer = new KafkaSqlValueDeserializer();
        KafkaConsumer<MessageKey, MessageValue> consumer = new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
        return consumer;
    }

    private void tryToConfigureSecurity(Properties props) {
        //Try to configure sasl for authentication
        if (securityEnabled) {
            props.putIfAbsent("security.protocol", protocol);
            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG, String.format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                    "  oauth.client.id=\"%s\" "+
                    "  oauth.client.secret=\"%s\" "+
                    "  oauth.token.endpoint.uri=\"%s\" ;", clientId, clientSecret, tokenEndpoint));
            props.putIfAbsent(SaslConfigs.SASL_MECHANISM, saslMechanism);
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, loginCallbackHandler);
        }
        //Try to configure the trustStore, if specified
        if (trustStoreLocation.isPresent() && trustStorePassword.isPresent() && trustStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, trustStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword.get());
        }
        //Finally, try to configure the keystore, if specified
        if (keyStoreLocation.isPresent() && keyStorePassword.isPresent() && keyPassword.isPresent() && keyStoreType.isPresent()) {
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, keyStoreType.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation.get());
            props.putIfAbsent(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStorePassword.get());
            props.putIfAbsent(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword.get());
        }
    }
}