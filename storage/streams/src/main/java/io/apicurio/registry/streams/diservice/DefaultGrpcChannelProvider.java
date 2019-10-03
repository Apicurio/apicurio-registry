package io.apicurio.registry.streams.diservice;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import org.apache.kafka.streams.state.HostInfo;

import java.util.function.Function;

/**
 * Default plain-text gRPC Channel provider.
 */
public class DefaultGrpcChannelProvider implements Function<HostInfo, Channel> {
    @Override
    public Channel apply(HostInfo hostInfo) {
        return ManagedChannelBuilder
            .forAddress(hostInfo.host(), hostInfo.port())
            .usePlaintext()
            .build();
    }
}
