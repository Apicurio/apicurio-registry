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

package io.apicurio.registry.utils.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsumerSkipRecordsSerializationExceptionHandler extends ConsumerChainedExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ConsumerSkipRecordsSerializationExceptionHandler.class);

    public ConsumerSkipRecordsSerializationExceptionHandler() {
    }

    public ConsumerSkipRecordsSerializationExceptionHandler(BiConsumer<Consumer<?, ?>, RuntimeException> nextHandler) {
        super(nextHandler);
    }

    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "partition (.*?)-(\\d+) at offset (\\d+)"
    );

    @Override
    public void accept(Consumer<?, ?> consumer, RuntimeException e) {
        if (e instanceof SerializationException) {
            Matcher m = EXCEPTION_PATTERN.matcher(e.getMessage());
            if (m.find()) {
                String topic = m.group(1);
                int partition = Integer.parseInt(m.group(2));
                long errorOffset = Long.parseLong(m.group(3));
                TopicPartition tp = new TopicPartition(topic, partition);
                long currentOffset = consumer.position(tp);
                log.error("SerializationException - skipping records in partition {} from offset {} up to and including error offset {}",
                        tp, currentOffset, errorOffset, e);
                consumer.seek(tp, errorOffset + 1);
            } else {
                super.accept(consumer, e);
            }
        } else {
            super.accept(consumer, e);
        }
    }
}
