package io.apicurio.registry.streams.distore;

import io.apicurio.registry.streams.diservice.DistributedService;
import io.grpc.Channel;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;

import java.util.Objects;
import java.util.function.Function;

/**
 * A read-only StateStore base class for stores that are distributed among KafkaStreams processing
 * nodes comprising the distributed streams application. It dispatches requests to stores on local and remote
 * KafkaStreams processing nodes that contain parts of the data which is looked up.
 */
public abstract class DistributedReadOnlyStateStore<K, V, ROS> extends DistributedService<K, ROS> {

    private final Serde<V> valSerde;

    /**
     * @param streams                The {@link KafkaStreams} application
     * @param localApplicationServer The {@link HostInfo} derived from the
     *                               {@link StreamsConfig#APPLICATION_SERVER_CONFIG application.server}
     *                               configuration property of local kafka streams node for the streams application.
     *                               This is used to identify requests for local store, bypassing gRPC calls
     * @param storeName              The name of the {@link ReadOnlyWindowStore} registered in the streams application
     * @param keySerde               The {@link Serde} for keys of the store
     * @param valSerde               The {@link Serde} for values of the store
     * @param grpcChannelProvider    A function that establishes gRPC {@link Channel} to a remote store service
     *                               for the given {@link HostInfo} parameter
     * @param parallel               {@code true} if lookups that need to query many stores in the cluster are
     *                               to be performed in parallel
     */
    public DistributedReadOnlyStateStore(
        KafkaStreams streams,
        HostInfo localApplicationServer,
        String storeName,
        Serde<K> keySerde, Serde<V> valSerde,
        Function<? super HostInfo, ? extends Channel> grpcChannelProvider,
        boolean parallel
    ) {
        super(streams, localApplicationServer, storeName, keySerde, grpcChannelProvider, parallel);
        this.valSerde = Objects.requireNonNull(valSerde, "valSerde");
    }

    @Override
    protected final ROS remoteServiceGrpcClient(String storeName, Channel channel, Serde<K> keySerde) {
        return remoteServiceGrpcClient(storeName, channel, keySerde, valSerde);
    }

    protected abstract ROS remoteServiceGrpcClient(String storeName, Channel channel, Serde<K> keySerde, Serde<V> valSerde);
}
