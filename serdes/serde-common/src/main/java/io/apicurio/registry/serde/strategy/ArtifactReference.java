/*
 * Copyright 2021 Red Hat
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

import io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl;

/**
 * There is a new implementation of this class that can be found here {@link io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl} and here {@linkio.apicurio.registry.resolver.strategy.ArtifactReference}
 * We keep this class for compatibilty
 *
 * This class holds the information that reference one Artifact in Apicurio Registry. It will always make
 * reference to an artifact in a group. Optionally it can reference to a specific version.
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class ArtifactReference extends ArtifactReferenceImpl {

    private ArtifactReference() {
        super();
    }

    public static ArtifactReference fromGlobalId(Long globalId) {
        return builder().globalId(globalId).build();
    }

    public static ArtifactReferenceBuilder builder(){
        return new ArtifactReferenceBuilder();
    }

    public static class ArtifactReferenceBuilder {

        private ArtifactReference reference;

        ArtifactReferenceBuilder() {
            reference = new ArtifactReference();
        }

        public ArtifactReferenceBuilder groupId(String groupId) {
            reference.setGroupId(groupId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder artifactId(String artifactId) {
            reference.setArtifactId(artifactId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder version(String version) {
            reference.setVersion(version);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder globalId(Long globalId) {
            reference.setGlobalId(globalId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReferenceBuilder contentId(Long contentId) {
            reference.setContentId(contentId);
            return ArtifactReferenceBuilder.this;
        }

        public ArtifactReference build() {
            return reference;
        }

    }
}
