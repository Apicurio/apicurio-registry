package io.apicurio.registry.streams.diservice;

import org.apache.kafka.common.serialization.Serde;

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * A {@link BiFunction} that returns an asynchronous {@link CompletionStage} result.
 *
 * @param <K>   the type of keys (which are also used for distribution decisions)
 * @param <REQ> the type of requests
 * @param <RES> the type of responses (wrapped into {@link CompletionStage} to be asynchronous)
 */
public interface AsyncBiFunctionService<K, REQ, RES> extends BiFunction<K, REQ, CompletionStage<RES>>, AutoCloseable {

    /**
     * An extension of {@link AsyncBiFunctionService} providing {@link Serde} implementations:
     * {@link #keySerde()}, {@link #reqSerde()} and {@link #resSerde()}.
     */
    interface WithSerdes<K, REQ, RES> extends AsyncBiFunctionService<K, REQ, RES> {
        Serde<K> keySerde();

        Serde<REQ> reqSerde();

        Serde<RES> resSerde();
    }
}
