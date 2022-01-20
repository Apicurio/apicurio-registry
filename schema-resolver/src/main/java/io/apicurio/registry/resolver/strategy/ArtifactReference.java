/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl.ArtifactReferenceBuilder;

/**
 * This class holds the information that reference one Artifact in Apicurio Registry. It will always make
 * reference to an artifact in a group. Optionally it can reference to a specific version.
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
public interface ArtifactReference {

    boolean hasValue();

    /**
     * @return the groupId
     */
    String getGroupId();

    /**
     * @return the artifactId
     */
    String getArtifactId();

    /**
     * @return the version
     */
    String getVersion();

    /**
     * @return the globalId
     */
    Long getGlobalId();

    /**
     * @return the contentId
     */
    Long getContentId();

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    int hashCode();

    /**
     * Logical equality. Two artifact references are equal, if they
     * MUST refer to the same artifact.
     */
    @Override
    boolean equals(Object obj);

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    String toString();

    public static ArtifactReference fromGlobalId(Long globalId) {
        return builder().globalId(globalId).build();
    }

    public static ArtifactReferenceBuilder builder(){
        return new ArtifactReferenceBuilder();
    }

}