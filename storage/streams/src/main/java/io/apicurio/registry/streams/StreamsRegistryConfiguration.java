package io.apicurio.registry.streams;

import com.google.common.collect.ImmutableMap;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.utils.ArtifactKeySerde;
import io.apicurio.registry.streams.utils.StateService;
import io.apicurio.registry.streams.utils.WaitForDataService;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.RegistryProperties;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import io.apicurio.registry.utils.streams.diservice.AsyncBiFunctionService;
import io.apicurio.registry.utils.streams.diservice.AsyncBiFunctionServiceGrpcLocalDispatcher;
import io.apicurio.registry.utils.streams.diservice.DefaultGrpcChannelProvider;
import io.apicurio.registry.utils.streams.diservice.DistributedAsyncBiFunctionService;
import io.apicurio.registry.utils.streams.diservice.LocalService;
import io.apicurio.registry.utils.streams.diservice.proto.AsyncBiFunctionServiceGrpc;
import io.apicurio.registry.utils.streams.distore.DistributedReadOnlyKeyValueStore;
import io.apicurio.registry.utils.streams.distore.ExtReadOnlyKeyValueStore;
import io.apicurio.registry.utils.streams.distore.FilterPredicate;
import io.apicurio.registry.utils.streams.distore.KeyValueSerde;
import io.apicurio.registry.utils.streams.distore.KeyValueStoreGrpcImplLocalDispatcher;
import io.apicurio.registry.utils.streams.distore.UnknownStatusDescriptionInterceptor;
import io.apicurio.registry.utils.streams.distore.proto.KeyValueStoreGrpc;
import io.apicurio.registry.utils.streams.ext.ForeachActionDispatcher;
import io.apicurio.registry.utils.streams.ext.Lifecycle;
import io.apicurio.registry.utils.streams.ext.LoggingStateRestoreListener;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class StreamsRegistryConfiguration {
    private static final Logger log = LoggerFactory.getLogger(StreamsRegistryConfiguration.class);

    private static void close(Object service) {
        if (service instanceof AutoCloseable) {
            try {
                ((AutoCloseable) service).close();
            } catch (Exception ignored) {
            }
        }
    }

    @Produces
    @ApplicationScoped
    public StreamsProperties streamsProperties(
        @RegistryProperties(
                value = {"registry.streams.common", "registry.streams.topology"},
                empties = {"ssl.endpoint.identification.algorithm="}
        ) Properties properties
    ) {
        return new StreamsPropertiesImpl(properties);
    }

    @Produces
    @ApplicationScoped
    public ProducerActions<Str.ArtifactKey, Str.StorageValue> storageProducer(
        @RegistryProperties(
                value = {"registry.streams.common", "registry.streams.storage-producer"},
                empties = {"ssl.endpoint.identification.algorithm="}
        ) Properties properties
    ) {
        return new AsyncProducer<>(
                properties,
                new ArtifactKeySerde().serializer(),
                ProtoSerde.parsedWith(Str.StorageValue.parser())
        );
    }

    public void stopStorageProducer(@Disposes ProducerActions<Str.ArtifactKey, Str.StorageValue> producer) throws Exception {
        producer.close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Produces
    @Singleton // required (cannot be ApplicationScoped), as we don't want proxy
    public KafkaClientSupplier kafkaClientSupplier(StreamsProperties properties) {
        KafkaClientSupplier kcs = new DefaultKafkaClientSupplier();
        if (!properties.ignoreAutoCreate()) {
            Map<String, Object> configMap = new HashMap(properties.getProperties());
            try (Admin admin = kcs.getAdmin(configMap)) {
                KafkaUtil.createTopics(admin, Collections.singleton(properties.getStorageTopic()));
            }
        }
        return kcs;
    }

    @Produces
    @Singleton
    public KafkaStreams storageStreams(
        StreamsProperties properties,
        KafkaClientSupplier kafkaClientSupplier, // this injection is here to create a dependency on previous auto-create topics code
        ForeachAction<? super Str.ArtifactKey, ? super Str.Data> dataDispatcher,
        ArtifactTypeUtilProviderFactory factory
    ) {
        Topology topology = new StreamsTopologyProvider(properties, dataDispatcher, factory).get();

        KafkaStreams streams = new KafkaStreams(topology, properties.getProperties(), kafkaClientSupplier);
        streams.setGlobalStateRestoreListener(new LoggingStateRestoreListener());

        return streams;
    }

    public void init(@Observes StartupEvent event, KafkaStreams streams) {
        streams.start();
    }

    public void destroy(@Observes ShutdownEvent event, KafkaStreams streams) {
        streams.close();
    }

    @Produces
    @Singleton
    public HostInfo storageLocalHost(StreamsProperties props) {
        String appServer = props.getApplicationServer();
        String[] hostPort = appServer.split(":");
        log.info("Application server gRPC: '{}'", appServer);
        return new HostInfo(hostPort[0], Integer.parseInt(hostPort[1]));
    }

    @Produces
    @ApplicationScoped
    public FilterPredicate<Str.ArtifactKey, Str.Data> filterPredicate() {
        return StreamsRegistryStorage.createFilterPredicate();
    }

    @Produces
    @ApplicationScoped
    public ExtReadOnlyKeyValueStore<Str.ArtifactKey, Str.Data> storageKeyValueStore(
        KafkaStreams streams,
        HostInfo storageLocalHost,
        StreamsProperties properties,
        FilterPredicate<Str.ArtifactKey, Str.Data> filterPredicate
    ) {
        return new DistributedReadOnlyKeyValueStore<>(
            streams,
            storageLocalHost,
            properties.getStorageStoreName(),
                new ArtifactKeySerde(), ProtoSerde.parsedWith(Str.Data.parser()),
            new DefaultGrpcChannelProvider(),
            true,
            filterPredicate
        );
    }

    public void destroyStorageStore(@Observes ShutdownEvent event, ExtReadOnlyKeyValueStore<Str.ArtifactKey, Str.Data> store) {
        close(store);
    }

    @Produces
    @ApplicationScoped
    public ReadOnlyKeyValueStore<Long, Str.TupleValue> globalIdKeyValueStore(
        KafkaStreams streams,
        HostInfo storageLocalHost,
        StreamsProperties properties
    ) {
        return new DistributedReadOnlyKeyValueStore<>(
            streams,
            storageLocalHost,
            properties.getGlobalIdStoreName(),
            Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser()),
            new DefaultGrpcChannelProvider(),
            true,
            (filter, id, tuple) -> true
        );
    }

    public void destroyGlobaIdStore(@Observes ShutdownEvent event, ReadOnlyKeyValueStore<Long, Str.TupleValue> store) {
        close(store);
    }

    @Produces
    @Singleton
    public ForeachActionDispatcher<Str.ArtifactKey, Str.Data> dataDispatcher() {
        return new ForeachActionDispatcher<>();
    }

    @Produces
    @Singleton
    public WaitForDataService waitForDataServiceImpl(
        ReadOnlyKeyValueStore<Str.ArtifactKey, Str.Data> storageKeyValueStore,
        ForeachActionDispatcher<Str.ArtifactKey, Str.Data> storageDispatcher
    ) {
        return new WaitForDataService(storageKeyValueStore, storageDispatcher);
    }

    @Produces
    @Singleton
    public LocalService<AsyncBiFunctionService.WithSerdes<Str.ArtifactKey, Long, Str.Data>> localWaitForDataService(
        WaitForDataService localService
    ) {
        return new LocalService<>(
            WaitForDataService.NAME,
            localService
        );
    }

    @Produces
    @ApplicationScoped
    @Current
    public AsyncBiFunctionService<Str.ArtifactKey, Long, Str.Data> waitForDataUpdateService(
        StreamsProperties properties,
        KafkaStreams streams,
        HostInfo storageLocalHost,
        LocalService<AsyncBiFunctionService.WithSerdes<Str.ArtifactKey, Long, Str.Data>> localWaitForDataUpdateService
    ) {
        return new DistributedAsyncBiFunctionService<>(
            streams,
            storageLocalHost,
            properties.getStorageStoreName(),
            localWaitForDataUpdateService,
            new DefaultGrpcChannelProvider()
        );
    }

    public void destroyWaitForDataUpdateService(@Observes ShutdownEvent event, @Current AsyncBiFunctionService<Str.ArtifactKey, Long, Str.Data> service) {
        close(service);
    }

    @Produces
    @Singleton
    public StateService stateServiceImpl(KafkaStreams streams) {
        return new StateService(streams);
    }

    @Produces
    @Singleton
    public LocalService<AsyncBiFunctionService.WithSerdes<Void, Void, KafkaStreams.State>> localStateService(
        StateService localService
    ) {
        return new LocalService<>(
            StateService.NAME,
            localService
        );
    }

    @Produces
    @ApplicationScoped
    @Current
    public AsyncBiFunctionService<Void, Void, KafkaStreams.State> stateService(
        KafkaStreams streams,
        HostInfo storageLocalHost,
        LocalService<AsyncBiFunctionService.WithSerdes<Void, Void, KafkaStreams.State>> localStateService
    ) {
        return new DistributedAsyncBiFunctionService<>(
            streams,
            storageLocalHost,
            "stateStore",
            localStateService,
            new DefaultGrpcChannelProvider()
        );
    }

    public void destroyStateService(@Observes ShutdownEvent event, @Current AsyncBiFunctionService<Void, Void, KafkaStreams.State> service) {
        close(service);
    }

    // gRPC server

    @Produces
    @ApplicationScoped
    public Lifecycle storageGrpcServer(
        HostInfo storageLocalHost,
        KeyValueStoreGrpc.KeyValueStoreImplBase storageStoreGrpcImpl,
        AsyncBiFunctionServiceGrpc.AsyncBiFunctionServiceImplBase storageAsyncBiFunctionServiceGrpcImpl
    ) {

        UnknownStatusDescriptionInterceptor unknownStatusDescriptionInterceptor =
            new UnknownStatusDescriptionInterceptor(
                ImmutableMap.of(
                    IllegalArgumentException.class, Status.INVALID_ARGUMENT,
                    IllegalStateException.class, Status.FAILED_PRECONDITION,
                    InvalidStateStoreException.class, Status.FAILED_PRECONDITION,
                    Throwable.class, Status.INTERNAL
                )
            );

        Server server = ServerBuilder
            .forPort(storageLocalHost.port())
            .addService(
                ServerInterceptors.intercept(
                    storageStoreGrpcImpl,
                    unknownStatusDescriptionInterceptor
                )
            )
            .addService(
                ServerInterceptors.intercept(
                    storageAsyncBiFunctionServiceGrpcImpl,
                    unknownStatusDescriptionInterceptor
                )
            )
            .build();

        return new Lifecycle() {
            @Override
            public void start() {
                try {
                    server.start();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void stop() {
                ConcurrentUtil
                    .<Server>consumer(Server::awaitTermination)
                    .accept(server.shutdown());
            }

            @Override
            public boolean isRunning() {
                return !(server.isShutdown() || server.isTerminated());
            }
        };
    }

    public void init(@Observes StartupEvent event, Lifecycle lifecycle) {
        lifecycle.start();
    }

    public void destroy(@Observes ShutdownEvent event, Lifecycle lifecycle) {
        lifecycle.stop();
    }

    @Produces
    @Singleton
    public KeyValueStoreGrpc.KeyValueStoreImplBase streamsKeyValueStoreGrpcImpl(
        KafkaStreams streams,
        StreamsProperties props,
        FilterPredicate<Str.ArtifactKey, Str.Data> filterPredicate
    ) {
        return new KeyValueStoreGrpcImplLocalDispatcher(
            streams,
            KeyValueSerde
                .newRegistry()
                .register(
                    props.getStorageStoreName(),
                    new ArtifactKeySerde(), ProtoSerde.parsedWith(Str.Data.parser())
                )
                .register(
                    props.getGlobalIdStoreName(),
                    Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser())
                ),
            filterPredicate
        );
    }

    @Produces
    @Singleton
    public AsyncBiFunctionServiceGrpc.AsyncBiFunctionServiceImplBase storageAsyncBiFunctionServiceGrpcImpl(
        LocalService<AsyncBiFunctionService.WithSerdes<Str.ArtifactKey, Long, Str.Data>> localWaitForDataService,
        LocalService<AsyncBiFunctionService.WithSerdes<Void, Void, KafkaStreams.State>> localStateService
    ) {
        return new AsyncBiFunctionServiceGrpcLocalDispatcher(
            Arrays.asList(localWaitForDataService, localStateService)
        );
    }

}
