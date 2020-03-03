package io.apicurio.registry.streams.diservice;

import io.grpc.Channel;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.HostInfo;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

public class DistributedAsyncBiFunctionService<K, REQ, RES>
    extends DistributedService<K, AsyncBiFunctionService<K, REQ, RES>>
    implements AsyncBiFunctionService<K, REQ, RES> {

    private final String serviceName;
    private final Serde<REQ> reqSerde;
    private final Serde<RES> resSerde;
    private final AsyncBiFunctionService<K, REQ, RES> localService;

    public DistributedAsyncBiFunctionService(
        KafkaStreams streams,
        HostInfo localApplicationServer,
        String storeName, // used for distributing services by key (local service needs access to this local store)
        LocalService<? extends AsyncBiFunctionService.WithSerdes<K, REQ, RES>> localService,
        Function<? super HostInfo, ? extends Channel> grpcChannelProvider
    ) {
        super(streams, localApplicationServer, storeName, localService.getService().keySerde(), grpcChannelProvider, false);
        this.serviceName = localService.getServiceName();
        this.reqSerde = localService.getService().reqSerde();
        this.resSerde = localService.getService().resSerde();
        this.localService = localService.getService();
    }

    @Override
    public CompletionStage<RES> apply(K key, REQ req) {
        return serviceForKey(key).apply(key, req);
    }

    @Override
    public Stream<CompletionStage<RES>> applyForStore() {
        return allServicesForStoreStream().flatMap(AsyncBiFunctionService::applyForStore);
    }

    @Override
    public Stream<CompletionStage<RES>> apply() {
        return allServicesStream().flatMap(AsyncBiFunctionService::apply);
    }

    @Override
    protected AsyncBiFunctionService<K, REQ, RES> localService(String storeName, KafkaStreams streams) {
        return localService;
    }

    @Override
    protected AsyncBiFunctionService<K, REQ, RES> remoteServiceGrpcClient(String storeName, Channel channel, Serde<K> keySerde) {
        return new AsyncBiFunctionServiceGrpcClient<>(serviceName, channel, keySerde, reqSerde, resSerde);
    }
}
