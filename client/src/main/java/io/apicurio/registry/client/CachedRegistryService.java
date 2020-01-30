/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.client;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.enterprise.inject.Vetoed;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
@Vetoed // not a bean
@Path("__dummy_hack_apicurio")
class CachedRegistryService implements RegistryService {

    private final RegistryService delegate;

    private final Map<String, NavigableMap<Integer, ArtifactMetaData>> amds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ArtifactMetaData>> cmds = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, VersionMetaData>> vmds = new ConcurrentHashMap<>();
    private final Map<Long, ArtifactMetaData> globalAMD = new ConcurrentHashMap<>();

    public CachedRegistryService() {
        // hack + client side only
        delegate = null;
    }

    public CachedRegistryService(RegistryService delegate) {
        this.delegate = delegate;
    }

    private RegistryService getDelegate() {
        if (delegate == null) {
            throw new IllegalStateException("Null registry service delegate!");
        }
        return delegate;
    }

    // Cached

    @Override
    public void reset() {
        amds.clear();
        vmds.clear();
        globalAMD.clear();
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        NavigableMap<Integer, ArtifactMetaData> map = amds.computeIfAbsent(artifactId, id -> new TreeMap<>());
        if (map.isEmpty()) {
            ArtifactMetaData amd = getDelegate().getArtifactMetaData(artifactId);
            map.put(amd.getVersion(), amd);
            globalAMD.put(amd.getGlobalId(), amd);
        }
        return map.lastEntry().getValue();
    }
    
    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactMetaDataByContent(java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) {
        String content = IoUtil.toString(data);
        Map<String, ArtifactMetaData> map = cmds.computeIfAbsent(artifactId, id -> new TreeMap<>());
        return map.computeIfAbsent(content, c -> {
            InputStream copy = IoUtil.toStream(content);
            ArtifactMetaData amd = getDelegate().getArtifactMetaDataByContent(artifactId, copy);
            globalAMD.put(amd.getGlobalId(), amd);
            return amd;
        });
    }
    
    /**
     * @see io.apicurio.registry.rest.IdsResource#getArtifactMetaDataByGlobalId(long)
     */
    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return globalAMD.computeIfAbsent(globalId, getDelegate()::getArtifactMetaDataByGlobalId);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        Map<Integer, VersionMetaData> map = vmds.computeIfAbsent(artifactId, id -> new ConcurrentHashMap<>());
        return map.computeIfAbsent(version, v -> getDelegate().getArtifactVersionMetaData(version, artifactId));
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        NavigableMap<Integer, ArtifactMetaData> map = amds.get(artifactId);
        if (map != null) {
            return map.keySet().stream().map(Long::new).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId, InputStream data) {
        CompletionStage<ArtifactMetaData> cs = getDelegate().createArtifact(xRegistryArtifactType, xRegistryArtifactId, data);
        return cs.thenApply(amd -> {
            NavigableMap<Integer, ArtifactMetaData> map = amds.computeIfAbsent(xRegistryArtifactId, id -> new TreeMap<>());
            map.put(amd.getVersion(), amd);
            globalAMD.put(amd.getGlobalId(), amd);
            return amd;
        });
    }

    @Override
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        CompletionStage<ArtifactMetaData> cs = getDelegate().updateArtifact(artifactId, xRegistryArtifactType, data);
        return cs.thenApply(amd -> {
            NavigableMap<Integer, ArtifactMetaData> map = amds.computeIfAbsent(artifactId, id -> new TreeMap<>());
            map.put(amd.getVersion(), amd);
            globalAMD.put(amd.getGlobalId(), amd);
            return amd;
        });
    }

    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        CompletionStage<VersionMetaData> cs = getDelegate().createArtifactVersion(artifactId, xRegistryArtifactType, data);
        return cs.thenApply(vmd -> {
            Map<Integer, VersionMetaData> map = vmds.computeIfAbsent(artifactId, id -> new ConcurrentHashMap<>());
            map.put(vmd.getVersion(), vmd);
            return vmd;
        });
    }

    // -- cannot cache response ...

    @Override
    public Response getLatestArtifact(String artifactId) {
        ArtifactMetaData amd = getArtifactMetaData(artifactId);
        // use cached metadata -- so we're in-sync
        return getArtifactVersion(amd.getVersion(), amd.getId());
    }

    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        return getDelegate().getArtifactVersion(version, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.IdsResource#getArtifactByGlobalId(long)
     */
    @Override
    public Response getArtifactByGlobalId(long globalId) {
        return getDelegate().getArtifactByGlobalId(globalId);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream content) {
        // no sense in caching this
        getDelegate().testUpdateArtifact(artifactId, xRegistryArtifactType, content);
    }

    // ---- Auto reset

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        getDelegate().updateArtifactState(artifactId, state);
        reset();
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        getDelegate().updateArtifactState(artifactId, state, version);
        reset();
    }

    @Override
    public void deleteArtifact(String artifactId) {
        getDelegate().deleteArtifact(artifactId);
        reset();
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        getDelegate().updateArtifactMetaData(artifactId, data);
        reset();
    }

    @Override
    public void deleteArtifactVersion(Integer version, String artifactId) {
        getDelegate().deleteArtifactVersion(version, artifactId);
        reset();
    }

    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        getDelegate().deleteArtifactVersionMetaData(version, artifactId);
        reset();
    }

    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        getDelegate().updateArtifactVersionMetaData(version, artifactId, data);
        reset();
    }

    // -- RULES

    @Override
    public Rule getArtifactRuleConfig(RuleType rule, String artifactId) {
        return getDelegate().getArtifactRuleConfig(rule, artifactId);
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule, String artifactId, Rule data) {
        return getDelegate().updateArtifactRuleConfig(rule, artifactId, data);
    }

    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {
        getDelegate().deleteArtifactRule(rule, artifactId);
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return getDelegate().listArtifactRules(artifactId);
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        getDelegate().createArtifactRule(artifactId, data);
    }

    @Override
    public void deleteArtifactRules(String artifactId) {
        getDelegate().deleteArtifactRules(artifactId);
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return getDelegate().getGlobalRuleConfig(rule);
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return getDelegate().updateGlobalRuleConfig(rule, data);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        getDelegate().deleteGlobalRule(rule);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return getDelegate().listGlobalRules();
    }

    @Override
    public void createGlobalRule(Rule data) {
        getDelegate().createGlobalRule(data);
    }

    @Override
    public void deleteAllGlobalRules() {
        getDelegate().deleteAllGlobalRules();
    }

    @Override
    public void close() throws Exception {
        getDelegate().close();
    }
}
