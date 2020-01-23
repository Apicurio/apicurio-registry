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

package io.apicurio.registry.storage.impl.jpa;

import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.MetaDataKeys;
import io.apicurio.registry.storage.impl.jpa.entity.Artifact;
import io.apicurio.registry.storage.impl.jpa.entity.MetaData;

import static io.apicurio.registry.storage.MetaDataKeys.ARTIFACT_ID;
import static io.apicurio.registry.storage.MetaDataKeys.CONTENT;
import static io.apicurio.registry.storage.MetaDataKeys.DESCRIPTION;
import static io.apicurio.registry.storage.MetaDataKeys.GLOBAL_ID;
import static io.apicurio.registry.storage.MetaDataKeys.NAME;
import static io.apicurio.registry.storage.MetaDataKeys.STATE;
import static io.apicurio.registry.storage.MetaDataKeys.VERSION;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManager;

public class MetaDataMapperUpdater {

//    private static Logger log = LoggerFactory.getLogger(MetaDataMapperUpdater.class);

    private final List<MetaData> existing;

    private final Map<String, String> added = new HashMap<>();

    public MetaDataMapperUpdater(List<MetaData> existing) {
        this.existing = existing;
    }

    public MetaDataMapperUpdater() {
        this.existing = Collections.emptyList();
    }

    public MetaDataMapperUpdater update(String key, String value) {
        added.put(key, value);
        return this;
    }

    public MetaDataMapperUpdater update(String key, Object value) {
        added.put(key, String.valueOf(value));
        return this;
    }

    public MetaDataMapperUpdater update(Map<String, String> map) {
        added.putAll(map);
        return this;
    }

    public MetaDataMapperUpdater update(List<MetaData> existing, String... keys) {
        Set<String> set = new HashSet<>(Arrays.asList(keys));
        existing.forEach(md -> {
            if (set.contains(md.getKey())) {
                update(md.getKey(), md.getValue());
            }
        });
        return this;
    }

    public MetaDataMapperUpdater update(Artifact artifact) {
        this.update(GLOBAL_ID, artifact.getGlobalId());
        this.update(ARTIFACT_ID, artifact.getArtifactId());
        this.update(VERSION, artifact.getVersion());
        this.update(CONTENT, new String(artifact.getContent(), StandardCharsets.UTF_8));
        this.update(STATE, artifact.getState().name());
        return this;
    }

    public MetaDataMapperUpdater update(EditableArtifactMetaDataDto metaData) {
        this.update(this.mapToMap(metaData));
        return this;
    }

    /**
     * Merge added into the existing
     */
    private Map<String, String> merge() {
        HashMap<String, String> res = new HashMap<>();
        existing.forEach(e -> res.put(e.getKey(), e.getValue()));
        res.putAll(added);
        return res;
    }

    /**
     * Persist new and update existing metadata entry,
     * do not remove existing ones.
     */
    public MetaDataMapperUpdater persistUpdate(EntityManager em, String artifactId, Long version) {
        Map<String, String> toPersist = new HashMap<>(added);

        existing.forEach(e -> {
            if (added.containsKey(e.getKey())) {
                toPersist.remove(e.getKey());
                e.setValue(added.get(e.getKey()));
                em.merge(e);
            }
        });

        toPersist.forEach((key, value) -> {
            if(value != null)
                em.persist(
                        MetaData.builder()
                                .artifactId(artifactId)
                                .version(version)
                                .key(key)
                                .value(value)
                                .build()
                );
        });

        return this;
    }

    public ArtifactMetaDataDto toArtifactMetaDataDto() {
        return MetaDataKeys.toArtifactMetaData(merge());
    }

    public ArtifactVersionMetaDataDto toArtifactVersionMetaDataDto() {
        return MetaDataKeys.toArtifactVersionMetaData(merge());
    }

    public Map<String, String> mapToMap(EditableArtifactMetaDataDto dto) {
        Map<String, String> res = new HashMap<>();
        res.put(NAME, dto.getName());
        res.put(DESCRIPTION, dto.getDescription());
        return res;
    }
}
