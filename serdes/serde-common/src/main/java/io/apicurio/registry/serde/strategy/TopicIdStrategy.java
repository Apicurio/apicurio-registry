/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.serde.strategy;

/**
 * @author Ales Justin
 */
public class TopicIdStrategy<T> implements ArtifactResolverStrategy<T> {

    /**
     * @see io.apicurio.registry.serde.strategy.ArtifactResolverStrategy#artifactReference(java.lang.String, boolean, java.lang.Object)
     */
    @Override
    public ArtifactReference artifactReference(String topic, boolean isKey, T schema) {
        return ArtifactReference.builder()
                .groupId(null)
                .artifactId(String.format("%s-%s", topic, isKey ? "key" : "value"))
                .build();
    }

    /**
     * @see io.apicurio.registry.serde.strategy.ArtifactResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
