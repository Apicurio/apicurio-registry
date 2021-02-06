package io.apicurio.registry.storage.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.types.ArtifactState;

/**
 * @author Ales Justin
 */
public abstract class SimpleMapRegistryStorage extends AbstractMapRegistryStorage {

    @Override
    protected Map<String, StoredContent> createContentMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createContentHashMap()
     */
    @Override
    protected Map<Long, String> createContentHashMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected StorageMap createStorageMap() {
        return new SimpleStorageMap();
    }

    @Override
    protected Map<Long, TupleId> createGlobalMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<String, String> createGlobalRulesMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected MultiMap<ArtifactKey, String, String> createArtifactRulesMap() {
        return new ConcurrentHashMultiMap<>();
    }

    @Override
    protected Map<String, String> createLogConfigurationMap() {
        return new ConcurrentHashMap<>();
    }
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createGroupsMap()
     */
    @Override
    protected Map<String, GroupMetaDataDto> createGroupsMap() {
        return new ConcurrentHashMap<>();
    }

    private static class ConcurrentHashMultiMap<K, MK, MV> implements MultiMap<K, MK, MV> {
        private final Map<K, Map<MK, MV>> delegate = new ConcurrentHashMap<>();

        @NotNull
        private Map<MK, MV> map(K key) {
            return delegate.compute(key, (k, m) -> (m == null ? new ConcurrentHashMap<>() : m));
        }

        @Override
        public Set<MK> keys(K key) {
            return map(key).keySet();
        }

        @Override
        public MV get(K key, MK mk) {
            return map(key).get(mk);
        }

        @Override
        public MV putIfPresent(K key, MK mk, MV mv) {
            // it's not previous value, but it's good enough
            return map(key).computeIfPresent(mk, (k, v) -> mv);
        }

        @Override
        public MV putIfAbsent(K key, MK mk, MV mv) {
            return map(key).putIfAbsent(mk, mv);
        }

        @Override
        public MV remove(K key, MK mk) {
            Map<MK, MV> map = delegate.get(key);
            if (map == null) {
                return null;
            }
            MV mv = map.remove(mk);
            if (map.isEmpty()) {
                delegate.remove(key);
            }
            return mv;
        }

        @Override
        public void remove(K key) {
            delegate.remove(key);
        }

        @Override
        public void putAll(Map<K, Map<MK, MV>> map) {
            for (Map.Entry<K, Map<MK, MV>> entry : map.entrySet()) {
                delegate.put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Map<K, Map<MK, MV>> asMap() {
            return new HashMap<>(delegate);
        }
    }

    private static class SimpleStorageMap implements StorageMap {
        private final Map<ArtifactKey, Map<Long, Map<String, String>>> root = new ConcurrentHashMap<>();
//
//        @Override
//        public Map<String, Map<Long, Map<String, String>>> asMap() {
//            return root;
//        }
//
//        @Override
//        public void putAll(Map<String, Map<Long, Map<String, String>>> map) {
//            root.putAll(map);
//        }

        @Override
        public Set<ArtifactKey> keySet() {
            return root.keySet();
        }

        @Override
        public Map<Long, Map<String, String>> get(ArtifactKey artifactKey) {
            Map<Long, Map<String, String>> map = root.get(artifactKey);
            if (map == null) {
                throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
            }
            return map;
        }

        @Override
        public Map<Long, Map<String, String>> compute(ArtifactKey artifactKey) {
            Map<Long, Map<String, String>> rval = root.compute(artifactKey, (a, m) -> m != null ? m : new ConcurrentHashMap<>());
            return rval;
        }

        @Override
        public void createVersion(ArtifactKey artifactKey, long version, Map<String, String> contents) {
            Map<String, String> previous = putIfAbsent(artifactKey, version, contents);
            while (previous != null) {
                version++;
                contents.put(MetaDataKeys.VERSION, Long.toString(version));
                previous = putIfAbsent(artifactKey, version, contents);
            }
        }

        private Map<String, String> putIfAbsent(ArtifactKey artifactKey, long version, Map<String, String> contents) {
            return get(artifactKey).putIfAbsent(version, contents);
        }

        @Override
        public void put(ArtifactKey artifactKey, String key, String value) {
            long version = compute(artifactKey).entrySet()
                    .stream()
                    .filter(AbstractMapRegistryStorage.statesFilter(ArtifactStateExt.ACTIVE_STATES))
                    .map(Map.Entry::getKey)
                    .max(Long::compareTo)
                    .orElseThrow(() -> new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId()));
            put(artifactKey, version, key, value);
        }

        @Override
        public void put(ArtifactKey artifactKey, long version, String key, String value) {
            Map<Long, Map<String, String>> map = get(artifactKey);
            Map<String, String> content = map.get(version);
            if (content == null) {
                throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }

            // skip this for states, any other metadata needs active artifact
            if (key.equals(MetaDataKeys.STATE) == false) {
                ArtifactState state = ArtifactStateExt.getState(content);
                ArtifactStateExt.validateState(ArtifactStateExt.ACTIVE_STATES, state, artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }

            content.put(key, value);
        }

        @Override
        public Long remove(ArtifactKey artifactKey, long version) {
            Map<String, String> content = removeInternal(artifactKey, version, true);
            return Long.parseLong(content.get(MetaDataKeys.VERSION));
        }

        private Map<String, String> removeInternal(ArtifactKey artifactKey, long version, boolean remove) {
            Map<Long, Map<String, String>> map = get(artifactKey);
            Map<String, String> content = remove ? map.remove(version) : map.get(version);
            if (content == null) {
                throw new VersionNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId(), version);
            }
            if (map.isEmpty()) {
                root.remove(artifactKey);
            }
            return content;
        }

        @Override
        public void remove(ArtifactKey artifactKey, long version, String key) {
            removeInternal(artifactKey, version, false).remove(key);
        }

        @Override
        public Map<Long, Map<String, String>> remove(ArtifactKey artifactKey) {
            Map<Long, Map<String, String>> map = root.remove(artifactKey);
            if (map == null) {
                throw new ArtifactNotFoundException(artifactKey.getGroupId(), artifactKey.getArtifactId());
            }
            return map;
        }
    }
}
