package io.apicurio.registry.streams.diservice;

import io.grpc.Channel;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.StreamsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A service base class for services that are distributed among {@link KafkaStreams} processing
 * nodes comprising the distributed streams application. It dispatches requests to services on local and remote
 * KafkaStreams processing nodes that contain parts of the data/functionality which is served up. The distribution
 * is performed given the key and storeName which is registered in the kafka streams application and the following
 * streams method: {@link KafkaStreams#metadataForKey(String, Object, Serializer)}.
 */
public abstract class DistributedService<K, S> implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DistributedService.class);

    private final KafkaStreams streams;
    private final HostInfo localApplicationServer;
    private final String storeName;
    private final Serde<K> keySerde;
    private final Function<? super HostInfo, ? extends Channel> grpcChannelProvider;
    private final boolean parallel;

    /**
     * @param streams                The {@link KafkaStreams} application
     * @param localApplicationServer The {@link HostInfo} derived from the
     *                               {@link StreamsConfig#APPLICATION_SERVER_CONFIG application.server}
     *                               configuration property of local kafka streams node for the streams application.
     *                               This is used to identify requests for local service, bypassing gRPC calls
     * @param storeName              The name of the store registered in the streams application and used for distribution
     *                               of keys among kafka streams processing nodes.
     * @param keySerde               the {@link Serde} for keys of the service which are also the distribution keys of the
     *                               corresponding store.
     * @param grpcChannelProvider    A function that establishes gRPC {@link Channel} to a remote service
     *                               for the given {@link HostInfo} parameter
     * @param parallel               {@code true} if service calls that need to dispatch to many local services in
     *                               the cluster are to be performed in parallel
     */
    public DistributedService(
        KafkaStreams streams,
        HostInfo localApplicationServer,
        String storeName,
        Serde<K> keySerde,
        Function<? super HostInfo, ? extends Channel> grpcChannelProvider,
        boolean parallel
    ) {
        this.streams = Objects.requireNonNull(streams, "streams");
        this.localApplicationServer = Objects.requireNonNull(localApplicationServer, "localApplicationServer");
        this.storeName = Objects.requireNonNull(storeName, "storeName");
        this.keySerde = Objects.requireNonNull(keySerde, "keySerde");
        this.grpcChannelProvider = Objects.requireNonNull(grpcChannelProvider, "grpcChannelProvider");
        this.parallel = parallel;
    }

    private final ConcurrentMap<HostInfo, S> hostInfo2service = new ConcurrentHashMap<>();

    protected Serde<K> getKeySerde() {
        return keySerde;
    }

    // AutoCloseable

    @Override
    public void close() {
        // let only one thread at a time perform the closing...
        synchronized (hostInfo2service) {
            Iterator<Map.Entry<HostInfo, S>> serviceIter = hostInfo2service.entrySet().iterator();
            while (serviceIter.hasNext()) {
                Map.Entry<HostInfo, S> entry = serviceIter.next();
                HostInfo hostInfo = entry.getKey();
                S service = entry.getValue();
                // only close clients to remote stores
                if (!localApplicationServer.equals(hostInfo)) {
                    try {
                        ((AutoCloseable) service).close();
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException("Exception occurred closing the service", e);
                    }
                }
                serviceIter.remove();
            }
        }
    }

    protected final S serviceForKey(K key) {
        StreamsMetadata smeta = streams.metadataForKey(storeName, key, keySerde.serializer());
        if (smeta == null) {
            throw new InvalidStateStoreException(
                "StreamsMetadata is null?! " +
                "Store-name: " + storeName + " " +
                "Key: " + key
            );
        }
        if (smeta == StreamsMetadata.NOT_AVAILABLE) {
            throw new InvalidStateStoreException(
                "StreamsMetadata is currently unavailable. " +
                "This can occur during rebalance operations. " +
                "Store-name: " + storeName + " " +
                "Key: " + key
            );
        }
        return serviceForHostInfo(smeta.hostInfo());
    }

    protected final Collection<S> allServicesForStore() {
        Collection<StreamsMetadata> smetas = streams.allMetadataForStore(storeName);
        if (smetas.isEmpty()) {
            throw new InvalidStateStoreException(
                "StreamsMetadata is currently unavailable. " +
                "This can occur during rebalance operations. " +
                "Store-name: " + storeName
            );
        }
        ArrayList<S> services = new ArrayList<>(smetas.size());
        for (StreamsMetadata smeta : smetas) {
            services.add(serviceForHostInfo(smeta.hostInfo()));
        }
        return services;
    }

    protected final Stream<S> allServicesForStoreStream() {
        Collection<S> services = allServicesForStore();
        // call multiple services in parallel if requested and there are more than one
        return parallel && services.size() > 1 ? services.parallelStream() : services.stream();
    }

    protected final Collection<S> allServices() {
        Collection<StreamsMetadata> smetas = streams.allMetadata();
        if (smetas.isEmpty()) {
            throw new StreamsException(
                "StreamsMetadata is currently unavailable. " +
                "This can occur during rebalance operations. "
            );
        }
        ArrayList<S> services = new ArrayList<>(smetas.size());
        for (StreamsMetadata smeta : smetas) {
            services.add(serviceForHostInfo(smeta.hostInfo()));
        }
        return services;
    }

    protected final Stream<S> allServicesStream() {
        Collection<S> services = allServices();
        // call multiple services in parallel if requested and there are more than one
        return parallel && services.size() > 1 ? services.parallelStream() : services.stream();
    }

    private S serviceForHostInfo(HostInfo hostInfo) {
        return hostInfo2service.computeIfAbsent(
            hostInfo,
            hInfo -> {
                if (localApplicationServer.equals(hInfo)) {
                    log.info("Obtaining local service '{}' for host info '{}'", storeName, hInfo);
                    // use local store if host info is local host info
                    return localService(storeName, streams);
                } else {
                    log.info("Obtaining remote service '{}' for host info '{}'", storeName, hInfo);
                    // connect to remote for other host info(s)
                    return remoteServiceGrpcClient(storeName, grpcChannelProvider.apply(hInfo), keySerde);
                }
            }
        );
    }

    protected abstract S localService(String storeName, KafkaStreams streams);

    protected abstract S remoteServiceGrpcClient(String storeName, Channel channel, Serde<K> keySerde);
}
