/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.events;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Serdes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.rnorth.ducttape.unreliables.Unreliables;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(KafkaEventsProfile.class)
@Tag(ApicurioTestTags.DOCKER)
@Disabled
public class KafkaEventsTest extends AbstractResourceTestBase {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testKafkaEvents() throws TimeoutException {

        Consumer<UUID, String> kafkaConsumer = createConsumer(
                Serdes.UUID().deserializer().getClass().getName(),
                Serdes.String().deserializer().getClass().getName(),
                KafkaEventsProfile.EVENTS_TOPIC);

        kafkaConsumer.subscribe(Collections.singletonList(KafkaEventsProfile.EVENTS_TOPIC));

        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        // {"openapi":"3.0.2"}
        Assertions.assertNotNull(jsonSchema);
        String content = IoUtil.toString(jsonSchema);

        String artifactId = TestUtils.generateArtifactId();

        try {
            createArtifact(artifactId, ArtifactType.JSON, content);
            createArtifactVersion(artifactId, ArtifactType.JSON, content);
        } catch ( Exception e ) {
            Assertions.fail(e);
        }

        int expectedRecordCount = 2;

        List<ConsumerRecord<UUID, String>> allRecords = new ArrayList<>();

        Unreliables.retryUntilTrue(40, TimeUnit.SECONDS, () -> {
            kafkaConsumer.poll(Duration.ofMillis(50))
                    .iterator()
                    .forEachRemaining(allRecords::add);

            return allRecords.size() >= expectedRecordCount;
        });

        List<String> events = allRecords.stream()
                .map(r -> {
                    return new String(r.headers().lastHeader("ce_type").value());
                })
                .collect(Collectors.toList());

//        assertLinesMatch(
//                Arrays.asList(RegistryEventType.ARTIFACT_CREATED.cloudEventType(), RegistryEventType.ARTIFACT_UPDATED.cloudEventType()),
//                events);

        assertTrue(
                events.containsAll(Arrays.asList(RegistryEventType.ARTIFACT_CREATED.cloudEventType(), RegistryEventType.ARTIFACT_UPDATED.cloudEventType()))
                );

    }

    private Consumer<UUID, String> createConsumer(String keyDeserializer, String valueDeserializer, String topicName) {
        Properties props = new Properties();
        props.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"));
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topicName);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        return new KafkaConsumer<>(props);
    }

}
