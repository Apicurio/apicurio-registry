package io.apicurio.registry.streams.utils;

import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.diservice.AsyncBiFunctionService;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a local implementation of our Data lookup AsyncBiFunctionService.
 * When the call gets properly dispatched, this is the impl that returns the real result.
 *
 * Since data handling is async, we might not have an updated result yet,
 * when we issue the dispatch request. So this impl registers a CompletableFuture,
 * and waits for an update. Once the proper update is received, previously registered CompletableFuture is completed.
 * Resulting in a distributed CompletableFuture callback (via gRPC).
 *
 * @author Ales Justin
 */
public class WaitForDataService implements AsyncBiFunctionService.WithSerdes<String, Long, Str.Data> {
    public static final String NAME = "WaitForDataService";

    private final Map<String, NavigableMap<Long, ResultCF>> waitingResults = new ConcurrentHashMap<>();

    private final ReadOnlyKeyValueStore<String, Str.Data> storageKeyValueStore;

    public WaitForDataService(
        ReadOnlyKeyValueStore<String, Str.Data> storageKeyValueStore,
        ForeachActionDispatcher<String, Str.Data> storageDispatcher
    ) {
        this.storageKeyValueStore = Objects.requireNonNull(storageKeyValueStore);
        storageDispatcher.register(this::dataUpdated);
    }

    /**
     * Notification (from transformer)
     */
    private void dataUpdated(String artifactId, Str.Data data) {
        if (data == null) {
            return;
        }
        // fast-path check if there are any registered futures
        if (waitingResults.containsKey(artifactId)) {
            // re-check under lock (performed by CHM on the bucket-level)
            waitingResults.compute(
                artifactId,
                (_artifactId, cfMap) -> {
                    if (cfMap == null) {
                        // might have been de-registered after fast-path check above
                        return null;
                    }

                    NavigableMap<Long, ResultCF> map = cfMap.headMap(data.getLastProcessedOffset(), true);
                    Iterator<Map.Entry<Long, ResultCF>> iter = map.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Long, ResultCF> next = iter.next();
                        next.getValue().complete(data);
                        iter.remove();
                    }

                    return cfMap.isEmpty() ? null : cfMap;
                }
            );
        }
    }

    @Override
    public void close() {
    }

    @Override
    public Serde<String> keySerde() {
        return Serdes.String();
    }

    @Override
    public Serde<Long> reqSerde() {
        return Serdes.Long();
    }

    @Override
    public Serde<Str.Data> resSerde() {
        return ProtoSerde.parsedWith(Str.Data.parser());
    }

    @Override
    public CompletionStage<Str.Data> apply(String artifactId, Long offset) {
        // 1st register the future
        ResultCF cf = new ResultCF(offset);
        register(artifactId, cf);
        // 2nd check the store if it contains data for an artifactId
        try {
            dataUpdated(artifactId, storageKeyValueStore.get(artifactId));
        } catch (Throwable e) {
            // exception looking up the store is propagated to cf...
            deregister(artifactId, cf);
            cf.completeExceptionally(e);
        }
        return cf;
    }

    private void register(String artifactId, ResultCF cf) {
        waitingResults.compute(
            artifactId,
            (_artifactId, cfMap) -> {
                if (cfMap == null) {
                    cfMap = new TreeMap<>();
                }
                cfMap.put(cf.offset, cf);
                return cfMap;
            }
        );
    }

    private void deregister(String artifactId, ResultCF cf) {
        waitingResults.compute(
            artifactId,
            (_artifactId, cfMap) -> {
                if (cfMap == null) {
                    return null;
                } else {
                    cfMap.remove(cf.offset);
                    // remove empty queue from map
                    return cfMap.isEmpty() ? null : cfMap;
                }
            }
        );
    }

    private static class ResultCF extends CompletableFuture<Str.Data> {
        final long offset;

        public ResultCF(long offset) {
            this.offset = offset;
        }
    }

}
