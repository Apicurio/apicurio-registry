package io.apicurio.registry.resolver.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArtifactReferenceImplTest {
    @Test
    void testEqualsReturnsTrueWhenContentHashMatches() {
        ArtifactReference artifact1 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo")
                .build();
        ArtifactReference artifact2 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo")
                .build();

        assertTrue(artifact1.equals(artifact2));
        assertTrue(artifact2.equals(artifact1));
    }

    @Test
    void testEqualsReturnsFalseWhenContentHashesDontMatch() {
        ArtifactReference artifact1 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("foo")
                .build();
        ArtifactReference artifact2 = new ArtifactReferenceImpl.ArtifactReferenceBuilder().contentHash("bar")
                .build();

        assertTrue(!artifact1.equals(artifact2));
        assertTrue(!artifact2.equals(artifact1));
    }
}
