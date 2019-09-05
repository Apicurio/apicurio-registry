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

package io.apicurio.registry.kafka;

import io.apicurio.registry.kafka.proto.Reg;
import io.apicurio.registry.kafka.utils.AsyncProducer;
import io.apicurio.registry.kafka.utils.ConsumerActions;
import io.apicurio.registry.kafka.utils.DynamicConsumerContainer;
import io.apicurio.registry.kafka.utils.KafkaProperties;
import io.apicurio.registry.kafka.utils.Oneof2;
import io.apicurio.registry.kafka.utils.ProducerActions;
import io.apicurio.registry.kafka.utils.ProtoSerde;
import io.apicurio.registry.kafka.utils.Seek;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaRegistryConfiguration.class);

    public static final String SCHEMA_TOPIC = "schema-topic";

    @Produces
    public Properties properties(InjectionPoint ip) {
        KafkaProperties kp = ip.getAnnotated().getAnnotation(KafkaProperties.class);
        String prefix = (kp != null ? kp.value() : "");
        Config config = ConfigProvider.getConfig();
        Optional<String> po = config.getOptionalValue("quarkus.profile", String.class);
        if (po.isPresent()) {
            String profile = po.get();
            if (profile.length() > 0) {
                prefix = "%" + profile + "." + prefix;
            }
        }

        Properties properties = new Properties();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                properties.put(key.substring(prefix.length()), config.getValue(key, String.class));
            }
        }
        return properties;
    }

    @Produces
    @ApplicationScoped
    public ProducerActions<Reg.UUID, Reg.SchemaValue> schemaProducer(
        @KafkaProperties("registry.kafka.schema-producer.") Properties properties
    ) {
        return new AsyncProducer<>(
            properties,
            ProtoSerde.parsedWith(Reg.UUID.parser()),
            ProtoSerde.parsedWith(Reg.SchemaValue.parser())
        );
    }

    public void stop(@Disposes ProducerActions<String, Reg.SchemaValue> producer) throws Exception {
        producer.close();
    }

    @Produces
    @ApplicationScoped
    public ConsumerActions.DynamicAssignment<Reg.UUID, Reg.SchemaValue> schemaContainer(
        @KafkaProperties("registry.kafka.schema-consumer.") Properties properties,
        KafkaRegistryStorageHandle handle
    ) {
        // persistent unique group id
        String groupId = properties.getProperty("group.id");
        if (groupId == null) {
            log.warn("No group.id set, creating one ... DEV env only!!");
            properties.put("group.id", UUID.randomUUID().toString());
        }

        return new DynamicConsumerContainer<>(
            properties,
            ProtoSerde.parsedWith(Reg.UUID.parser()),
            ProtoSerde.parsedWith(Reg.SchemaValue.parser()),
            Oneof2.first(handle::consumeSchemaValue)
        );
    }

    public void init(@Observes StartupEvent event, ConsumerActions.DynamicAssignment<Reg.UUID, Reg.SchemaValue> container) {
        container.start();
        container.addTopicPartition(new TopicPartition(SCHEMA_TOPIC, 0), Seek.FROM_BEGINNING.offset(0));
    }

    public void destroy(@Observes ShutdownEvent event, ConsumerActions.DynamicAssignment<Reg.UUID, Reg.SchemaValue> container) {
        container.removeTopicParition(new TopicPartition(SCHEMA_TOPIC, 0));
        container.stop();
    }
}
