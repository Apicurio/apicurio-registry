package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentCapabilities;
import io.apicurio.registry.rest.v3.beans.AgentSearchResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;

/**
 * Applies the structured agent filters locally.
 *
 * <p>Needed because those filters map to {@code SearchFilterType.structure}, which is handled only
 * by {@code ElasticsearchSearchService}. On the SQL path the type falls through to the default case
 * in {@code SqlSearchRepository} and throws {@code RegistryStorageException}, so a peer backed by
 * SQL, KafkaSQL or in-memory storage cannot service a skill or capability filter at all (#8058).
 *
 * <p>Rather than dropping such a peer from the results, the coordinator re-queries it without the
 * structured filters and applies them here. The result set is identical; only the place the
 * filtering happens differs, and the source is reported as
 * {@link PeerSearchOutcome.Status#DEGRADED} so the caller knows.
 */
@ApplicationScoped
public class AgentResultFilter {

    /**
     * Keeps only the agents that satisfy every requested skill and capability.
     */
    public List<AgentSearchResult> apply(List<AgentSearchResult> agents, List<String> skills,
            List<String> capabilities) {
        return agents.stream()
                .filter(agent -> matchesSkills(agent, skills))
                .filter(agent -> matchesCapabilities(agent, capabilities))
                .toList();
    }

    private boolean matchesSkills(AgentSearchResult agent, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        List<String> declared = agent.getSkills();
        if (declared == null) {
            return false;
        }
        return declared.containsAll(required);
    }

    /**
     * Capability filters arrive as {@code name} or {@code name:true} / {@code name:false}, matching
     * the parsing the local search endpoint already does.
     */
    private boolean matchesCapabilities(AgentSearchResult agent, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        AgentCapabilities declared = agent.getCapabilities();
        for (String capability : required) {
            String[] parts = capability.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            boolean wanted = parts.length < 2 || !"false".equals(parts[1]);
            if (valueOf(declared, key) != wanted) {
                return false;
            }
        }
        return true;
    }

    /**
     * SPIKE: only the capabilities carried on {@code AgentSearchResult} can be evaluated here.
     * A full implementation would need the stored Agent Card, since arbitrary boolean capabilities
     * and {@code capabilities.extensions} never reach this bean.
     */
    private boolean valueOf(AgentCapabilities capabilities, String key) {
        if (capabilities == null) {
            return false;
        }
        return switch (key) {
            case "streaming" -> Boolean.TRUE.equals(capabilities.getStreaming());
            case "pushnotifications" -> Boolean.TRUE.equals(capabilities.getPushNotifications());
            default -> false;
        };
    }
}
