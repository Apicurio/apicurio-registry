package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Test-only {@link ArtifactVersionWriteHook} that holds an import between tools: a write of a tool
 * artifact with a gate waits until the test completes the gate.
 * <p>
 * The bean is active in every Quarkus test, so it ignores all writes except those to groups starting
 * with {@link #GROUP_PREFIX}, and every wait is bounded.
 * </p>
 */
@ApplicationScoped
public class McpImportGateHook implements ArtifactVersionWriteHook {

    static final String GROUP_PREFIX = "mcp-gate-";

    private final Map<String, CompletableFuture<Void>> gates = new ConcurrentHashMap<>();
    private final Set<String> reached = ConcurrentHashMap.newKeySet();

    /** Holds the write of this tool artifact until the returned gate is completed. */
    CompletableFuture<Void> hold(String artifactId) {
        return gates.computeIfAbsent(artifactId, id -> new CompletableFuture<>());
    }

    /** Whether the write of this tool artifact has reached its gate. */
    boolean reached(String artifactId) {
        return reached.contains(artifactId);
    }

    void reset() {
        gates.values().forEach(gate -> gate.complete(null));
        gates.clear();
        reached.clear();
    }

    @Override
    public ContentWrapperDto prepareContent(VersionWriteContext context, TypedContent content) {
        String groupId = context.getGa().getRawGroupIdWithNull();
        if (groupId == null || !groupId.startsWith(GROUP_PREFIX)) {
            return null;
        }
        CompletableFuture<Void> gate = gates.get(context.getGa().getRawArtifactId());
        if (gate != null) {
            reached.add(context.getGa().getRawArtifactId());
            try {
                gate.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("The test did not open the gate", e);
            }
        }
        return null;
    }
}
