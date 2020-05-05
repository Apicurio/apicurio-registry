package io.apicurio.registry.streams.utils;

import io.apicurio.registry.streams.diservice.AsyncBiFunctionService;
import io.apicurio.registry.utils.kafka.SelfSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Return KafkaStream state.
 *
 * @author Ales Justin
 */
public class StateService implements AsyncBiFunctionService.WithSerdes<Void, Void, KafkaStreams.State> {
    public static final String NAME = "StateService";

    private final KafkaStreams streams;

    public StateService(KafkaStreams streams) {
        this.streams = streams;
    }

    @Override
    public void close() {
    }

    @Override
    public Serde<Void> keySerde() {
        return SelfSerde.VOID;
    }

    @Override
    public Serde<Void> reqSerde() {
        return SelfSerde.VOID;
    }

    @Override
    public Serde<KafkaStreams.State> resSerde() {
        return new StateSerde();
    }

    @Override
    public Stream<CompletionStage<KafkaStreams.State>> apply() {
        return Stream.of(apply(null, null));
    }

    @Override
    public CompletionStage<KafkaStreams.State> apply(Void v1, Void v2) {
        return CompletableFuture.completedFuture(streams.state());
    }
}
