/*
 * Copyright 2019 Red Hat
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
package io.apicurio.tests.converters;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import static io.apicurio.tests.Constants.CLUSTER;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(CLUSTER)
public class KafkaConnectConverterIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConnectConverterIT.class);

    public static final byte AVRO_MAGIC_BYTE = 0x0;

    @Test
    public void testConvertToAvro() throws Exception {
        try (Connection connection = getConnection(postgres);
                Statement statement = connection.createStatement();
                KafkaConsumer<byte[], byte[]> consumer = getConsumerBytes(kafka)) {

            statement.execute("drop schema if exists todo cascade");
            statement.execute("create schema todo");
            statement.execute("create table todo.Todo (id int8 not null, title varchar(255), primary key (id))");
            statement.execute("alter table todo.Todo replica identity full");
            statement.execute("insert into todo.Todo values (1, 'Be Awesome')");

            debeziumContainer.registerConnector("my-connector-avro", getConfiguration(
                    2, "io.apicurio.registry.utils.converter.AvroConverter",
                    "key.converter.apicurio.registry.converter.serializer", "io.apicurio.registry.utils.serde.AvroKafkaSerializer",
                    "key.converter.apicurio.registry.converter.deserializer", "io.apicurio.registry.utils.serde.AvroKafkaDeserializer",
                    "value.converter.apicurio.registry.converter.serializer", "io.apicurio.registry.utils.serde.AvroKafkaSerializer",
                    "value.converter.apicurio.registry.converter.deserializer", "io.apicurio.registry.utils.serde.AvroKafkaDeserializer"));

            consumer.subscribe(Arrays.asList("dbserver2.todo.todo"));

            List<ConsumerRecord<byte[], byte[]>> changeEvents = drain(consumer, 1);

            // Verify magic byte of Avro messages
            assertTrue(changeEvents.get(0).key()[0] == AVRO_MAGIC_BYTE);
            assertTrue(changeEvents.get(0).value()[0] == AVRO_MAGIC_BYTE);

            consumer.unsubscribe();
        }
    }

    private Connection getConnection(PostgreSQLContainer<?> postgresContainer) throws SQLException {
        return DriverManager.getConnection(postgresContainer.getJdbcUrl(), postgresContainer.getUsername(),
            postgresContainer.getPassword());
    }

    private KafkaConsumer<byte[], byte[]> getConsumerBytes(KafkaContainer kafkaContainer) {
        return new KafkaConsumer<>(
            ImmutableMap.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
            new ByteArrayDeserializer(),
            new ByteArrayDeserializer());
    }

    private <T> List<ConsumerRecord<T, T>> drain(KafkaConsumer<T, T> consumer, int expectedRecordCount) {
        List<ConsumerRecord<T, T>> allRecords = new ArrayList<>();

        Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(50))
                .iterator()
                .forEachRemaining(allRecords::add);

            return allRecords.size() == expectedRecordCount;
        });

        return allRecords;
    }

    private ConnectorConfiguration getConfiguration(int id, String converter, String... options) {
        final String apicurioUrl = TestUtils.isExternalRegistry() ?
                TestUtils.getRegistryUrl()
                :
                "http://host.testcontainers.internal:8081/";

        // host, database, user etc. are obtained from the container
        final ConnectorConfiguration config = ConnectorConfiguration.forJdbcContainer(postgres)
            .with("database.server.name", "dbserver" + id)
            .with("slot.name", "debezium_" + id)
            .with("key.converter", converter)
            .with("key.converter.apicurio.registry.url", apicurioUrl)
            .with("key.converter.apicurio.registry.global-id", "io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy")
            .with("value.converter.apicurio.registry.url", apicurioUrl)
            .with("value.converter", converter)
            .with("value.converter.apicurio.registry.global-id", "io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy");

        if (options != null && options.length > 0) {
            for (int i = 0; i < options.length; i += 2) {
                config.with(options[i], options[i + 1]);
            }
        }
        return config;
    }

    KafkaContainer kafka;
    PostgreSQLContainer<?> postgres;
    DebeziumContainer debeziumContainer;

    @BeforeEach
    public void startContainers() {
        if (!TestUtils.isExternalRegistry()) {
            Testcontainers.exposeHostPorts(8081);
        }

        Testcontainers.exposeHostPorts(9092);
        kafka = new KafkaContainer();
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        kafka.addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        kafka.addExposedPorts(9092);
        kafka.withCreateContainerCmdModifier(cmd -> {
            cmd
                    .withHostName("localhost")
                    .withPortBindings(new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)));
        });
        kafka.start();

        Network network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>("debezium/postgres:11")
              .withNetwork(network)
              .withNetworkAliases("postgres");
        postgres.start();

        ImageFromDockerfile apicurioDebeziumImage = new ImageFromDockerfile()
              .withDockerfileFromBuilder(builder -> builder
                  .from("debezium/connect:1.1.1.Final")
                  .env("KAFKA_CONNECT_DEBEZIUM_DIR", "$KAFKA_CONNECT_PLUGINS_DIR/debezium-connector-postgres")
                  .env("APICURIO_VERSION", "1.1.2.Final")
                  .run("cd $KAFKA_CONNECT_DEBEZIUM_DIR && curl https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-distro-connect-converter/$APICURIO_VERSION/apicurio-registry-distro-connect-converter-$APICURIO_VERSION-converter.tar.gz | tar xzv")
                  .build());

        debeziumContainer = new DebeziumContainer(apicurioDebeziumImage)
              .withNetwork(network)
              .withEnv("BOOTSTRAP_SERVERS", "host.testcontainers.internal:9092")
              .withLogConsumer(new Slf4jLogConsumer(LOGGER));
        debeziumContainer.start();

    }

    @AfterEach
    public void stopContainers() {
        Stream.of(
                kafka, postgres, debeziumContainer)
            .forEach(c -> {
                if (c != null) {
                    c.stop();
                }
            });
    }
}
