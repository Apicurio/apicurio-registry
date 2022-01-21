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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import io.apicurio.tests.serdes.apicurio.SerdesTester.DataGenerator;
import io.apicurio.tests.serdes.apicurio.SerdesTester.Validator;
import io.apicurio.tests.serdes.apicurio.Tester.TesterBuilder;

/**
 * @author Fabian Martinez
 */
public class SimpleSerdesTesterBuilder<P, C> implements TesterBuilder {

    protected int batchCount = 1;
    protected int batchSize = 10;

    protected DataGenerator<P> dataGenerator;
    protected Predicate<C> dataValidator;

    protected Validator afterProduceValidator;

    protected String topic;

    protected Class<?> artifactResolverStrategy;

    protected Class<?> serializer;
    protected Class<?> deserializer;

    protected Properties producerProperties = new Properties();
    protected Properties consumerProperties = new Properties();

    public SimpleSerdesTesterBuilder() {
        super();
    }

    public SimpleSerdesTesterBuilder<P, C> withMessages(int batchCount, int batchSize) {
        this.batchCount = batchCount;
        this.batchSize = batchSize;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withCommonProperty(String key, String value) {
        producerProperties.put(key, value);
        consumerProperties.put(key, value);
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withProducerProperty(String key, String value) {
        producerProperties.put(key, value);
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withConsumerProperty(String key, String value) {
        consumerProperties.put(key, value);
        return this;
    }

    public <U extends Serializer<?>> SimpleSerdesTesterBuilder<P, C> withSerializer(Class<U> serializer) {
        this.serializer = serializer;
        return this;
    }

    public <U extends Deserializer<?>> SimpleSerdesTesterBuilder<P, C> withDeserializer(Class<U> deserializer) {
        this.deserializer = deserializer;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withStrategy(Class<?> strategy) {
        this.artifactResolverStrategy = strategy;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withDataGenerator(DataGenerator<P> generator) {
        this.dataGenerator = generator;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withDataValidator(Predicate<C> validator) {
        this.dataValidator = validator;
        return this;
    }

    public SimpleSerdesTesterBuilder<P, C> withAfterProduceValidator(Validator afterProduceValidator) {
        this.afterProduceValidator = afterProduceValidator;
        return this;
    }

    /**
     * @see io.apicurio.tests.serdes.apicurio.SerdesTester.TesterBuilder#build()
     */
    @Override
    public Tester build() {
        Objects.requireNonNull(producerProperties);
        Objects.requireNonNull(consumerProperties);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(artifactResolverStrategy);
        Objects.requireNonNull(dataGenerator);
        Objects.requireNonNull(deserializer);
        Objects.requireNonNull(dataValidator);
        return new SimpleSerdesTester();
    }


    private class SimpleSerdesTester extends SerdesTester<String, P, C> implements Tester {

        /**
         * @see io.apicurio.tests.serdes.apicurio.Tester#test()
         */
        @Override
        public void test() throws Exception {
            Producer<String, P> producer = this.createProducer(producerProperties, StringSerializer.class, serializer, topic, artifactResolverStrategy);

            boolean autoCloseByProduceOrConsume = batchCount == 1;
            setAutoClose(autoCloseByProduceOrConsume);

            try {
                for (int i = 0; i < batchCount; i++) {
                    this.produceMessages(producer, topic, dataGenerator, batchSize);
                }
            } finally {
                if (!autoCloseByProduceOrConsume) {
                    producer.close();
                }
            }

            if (afterProduceValidator != null) {
                assertTrue(afterProduceValidator.validate(), "After produce validation failed");
            }

            Consumer<String, C> consumer = this.createConsumer(consumerProperties, StringDeserializer.class, deserializer, topic);

            int messageCount = batchCount * batchSize;
            try {
                this.consumeMessages(consumer, topic, messageCount, dataValidator);
            } finally {
                if (!autoCloseByProduceOrConsume) {
                    consumer.close();
                }
            }

        }

    }


}
