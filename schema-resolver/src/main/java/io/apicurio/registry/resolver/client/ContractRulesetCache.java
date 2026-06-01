package io.apicurio.registry.resolver.client;

import io.apicurio.registry.rest.client.models.ContractRuleSet;

import java.util.concurrent.ConcurrentHashMap;

public class ContractRulesetCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public ContractRulesetCache(long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000;
    }

    public ContractRuleSet get(String groupId, String artifactId, String version) {
        String key = buildKey(groupId, artifactId, version);
        CachedEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.ruleset;
    }

    public void put(String groupId, String artifactId, String version, ContractRuleSet ruleset) {
        String key = buildKey(groupId, artifactId, version);
        cache.put(key, new CachedEntry(ruleset, System.currentTimeMillis() + ttlMillis));
    }

    private String buildKey(String groupId, String artifactId, String version) {
        String g = groupId != null ? groupId : "default";
        String v = version != null ? version : "_artifact_";
        return g + "/" + artifactId + "/" + v;
    }

    private static class CachedEntry {
        final ContractRuleSet ruleset;
        final long expiresAt;

        CachedEntry(ContractRuleSet ruleset, long expiresAt) {
            this.ruleset = ruleset;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
