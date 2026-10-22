package io.apicurio.registry.resolver.client;

import io.apicurio.registry.rest.client.models.ContractRuleSet;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ContractRulesetCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<String, CachedEntry<ContractRuleSet>> rulesetCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<List<String>>> versionListCache = new ConcurrentHashMap<>();

    public ContractRulesetCache(long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000;
    }

    public ContractRuleSet get(String groupId, String artifactId, String version) {
        String key = buildKey(groupId, artifactId, version);
        CachedEntry<ContractRuleSet> entry = rulesetCache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value;
    }

    public void put(String groupId, String artifactId, String version, ContractRuleSet ruleset) {
        String key = buildKey(groupId, artifactId, version);
        rulesetCache.put(key, new CachedEntry<>(ruleset, System.currentTimeMillis() + ttlMillis));
    }

    public List<String> getVersionList(String groupId, String artifactId) {
        String key = buildKey(groupId, artifactId, "_versions_");
        CachedEntry<List<String>> entry = versionListCache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value;
    }

    public void putVersionList(String groupId, String artifactId, List<String> versions) {
        String key = buildKey(groupId, artifactId, "_versions_");
        versionListCache.put(key, new CachedEntry<>(versions, System.currentTimeMillis() + ttlMillis));
    }

    private String buildKey(String groupId, String artifactId, String version) {
        String g = groupId != null ? groupId : "default";
        String v = version != null ? version : "_artifact_";
        return g + "/" + artifactId + "/" + v;
    }

    private static class CachedEntry<T> {
        final T value;
        final long expiresAt;

        CachedEntry(T value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
