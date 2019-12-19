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

package io.apicurio.registry.search.client.kafka;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.client.SearchResponse;
import io.apicurio.registry.search.client.SearchResults;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;

import static io.apicurio.registry.search.client.SearchUtil.property;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public class KafkaSearchClient implements SearchClient {

    private final String topic;
    private final ProducerActions<String, Search.Artifact> producer;

    public KafkaSearchClient(Properties properties) {
        topic = property(properties, "search.topic", "search-topic");
        String servers = property(properties, "search.bootstrap-servers", "localhost:9092");
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        producer = new AsyncProducer<>(
            properties,
            Serdes.String().serializer(),
            ProtoSerde.parsedWith(Search.Artifact.parser())
        );
    }

    @Override
    public CompletionStage<Boolean> initialize(boolean reset) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    @Override
    public CompletionStage<SearchResponse> index(Search.Artifact artifact) {
        ProducerRecord<String, Search.Artifact> record = new ProducerRecord<>(
            topic,
            artifact.getArtifactId(),
            artifact
        );
        return producer.apply(record).thenApply(KafkaSearchResponse::new);
    }

    @Override
    public CompletionStage<SearchResults> search(String query) {
        throw new UnsupportedOperationException("Query is not supported!");
    }

    @Override
    public void close() {
        IoUtil.closeIgnore(producer);
    }
}
