package io.apicurio.deployment.manual;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface RegistryRunner {

    void start(int nodeId, Instant startingLine, String image, String bootstrapServers, List<String> args, BiConsumer<String, RegistryRunner> reporter);

    Map<String, Object> getReport();

    int getNodeId();

    String getClientURL();

    boolean isStarted();

    boolean isStopped();

    void stop();

    void stopAndWait();
}