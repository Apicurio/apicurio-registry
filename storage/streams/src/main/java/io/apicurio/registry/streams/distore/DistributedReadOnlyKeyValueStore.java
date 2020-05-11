package io.apicurio.registry.streams.distore;

import io.grpc.Channel;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.function.Function;
import java.util.stream.Stream;

import static io.apicurio.registry.streams.distore.StreamToKeyValueIteratorAdapter.toStream;

/**
 * A {@link ReadOnlyKeyValueStore} that is distributed among KafkaStreams processing nodes comprising
 * the distributed streams application. It dispatches requests to stores on local and remote
 * KafkaStreams processing nodes that contain parts of the data which is looked up.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class DistributedReadOnlyKeyValueStore<K, V>
    extends DistributedReadOnlyStateStore<K, V, ExtReadOnlyKeyValueStore<K, V>>
    implements ExtReadOnlyKeyValueStore<K, V> {

    /**
     * @param streams                The {@link KafkaStreams} application
     * @param localApplicationServer The {@link HostInfo} derived from the
     *                               {@link StreamsConfig#APPLICATION_SERVER_CONFIG application.server}
     *                               configuration property of local kafka streams node for the streams application.
     *                               This is used to identify requests for local store, bypassing gRPC calls
     * @param storeName              The name of the {@link ReadOnlyKeyValueStore} registered in the streams application
     * @param keySerde               The {@link Serde} for keys of the store
     * @param valSerde               The {@link Serde} for values of the store
     * @param grpcChannelProvider    A function that establishes gRPC {@link Channel} to a remote store service
     *                               for the given {@link HostInfo} parameter
     * @param parallel               {@code true} if lookups that need to query many stores in the cluster are
     *                               to be performed in parallel
     * @param filterPredicate        filter predicate to filter out keys and values
     */
    public DistributedReadOnlyKeyValueStore(
        KafkaStreams streams,
        HostInfo localApplicationServer,
        String storeName,
        Serde<K> keySerde, Serde<V> valSerde,
        Function<? super HostInfo, ? extends Channel> grpcChannelProvider,
        boolean parallel,
        FilterPredicate<K, V> filterPredicate
    ) {
        super(
            streams,
            localApplicationServer,
            storeName,
            keySerde,
            valSerde,
            grpcChannelProvider,
            parallel
        );
        this.filterPredicate = filterPredicate;
    }

    private final FilterPredicate<K, V> filterPredicate;

    @Override
    protected ExtReadOnlyKeyValueStore<K, V> localService(String storeName, KafkaStreams streams) {
        ReadOnlyKeyValueStore<K, V> delegate = streams.store(storeName, QueryableStoreTypes.keyValueStore());
        return new ExtReadOnlyKeyValueStoreImpl<>(delegate, filterPredicate);
    }

    @Override
    protected ExtReadOnlyKeyValueStore<K, V> remoteServiceGrpcClient(String storeName, Channel channel, Serde<K> keySerde, Serde<V> valSerde) {
        return new ReadOnlyKeyValueStoreGrpcClient<>(storeName, channel, keySerde, valSerde);
    }

    @Override
    public Stream<K> allKeys() {
        return allServicesForStoreStream().flatMap(ExtReadOnlyKeyValueStore::allKeys);
    }

    @Override
    public Stream<KeyValue<K, V>> filter(String filter, String over) {
        return allServicesForStoreStream().flatMap(store -> store.filter(filter, over));
    }

    // ReadOnlyKeyValueStore<K, V> implementation

    @Override
    public V get(K key) {
        return serviceForKey(key).get(key);
    }

    @Override
    public KeyValueIterator<K, V> range(K from, K to) {
        return new StreamToKeyValueIteratorAdapter<>(
            allServicesForStoreStream()
                .flatMap(store -> toStream(store.range(from, to)))
        );
    }

    @Override
    public KeyValueIterator<K, V> all() {
        return new StreamToKeyValueIteratorAdapter<>(
            allServicesForStoreStream()
                .flatMap(store -> toStream(store.all()))
        );
    }

    @Override
    public long approximateNumEntries() {
        return allServicesForStoreStream()
            .mapToLong(ReadOnlyKeyValueStore::approximateNumEntries)
            .sum();
    }
}
