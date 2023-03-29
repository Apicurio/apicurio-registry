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
package io.apicurio.registry.resolver.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ArtifactReferenceImplTest {
    @Test
    void testEqualsReturnsTrueWhenContentHashMatches() {
        ArtifactReference artifact1 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo").build();
        ArtifactReference artifact2 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo").build();

        assertTrue(artifact1.equals(artifact2));
        assertTrue(artifact2.equals(artifact1));
    }

    @Test
    void testEqualsReturnsFalseWhenContentHashesDontMatch() {
        ArtifactReference artifact1 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo").build();
        ArtifactReference artifact2 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("bar").build();

        assertTrue(!artifact1.equals(artifact2));
        assertTrue(!artifact2.equals(artifact1));
    }
}
