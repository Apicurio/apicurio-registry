package io.apicurio.registry.federation;

import java.util.List;

/**
 * The agent search a caller asked for, as passed on to every source. Filters are the ones of
 * {@code GET /.well-known/agents}; {@code limit} is the per-source limit.
 */
public record PeerQuery(String name, List<String> skills, List<String> capabilities, List<String> inputModes,
        List<String> outputModes, int limit) {

    public PeerQuery {
        skills = skills == null ? List.of() : List.copyOf(skills);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        inputModes = inputModes == null ? List.of() : List.copyOf(inputModes);
        outputModes = outputModes == null ? List.of() : List.copyOf(outputModes);
    }
}
