package io.apicurio.registry.content.refs.resolve;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistryUrlReferenceResolverTest {

    @Test
    void registryReferenceNameRemovesOriginAndPreservesFragment() {
        String fullReference = "http://localhost:8080/apis/registry/v3/groups/master/artifacts/"
                + "kafka-bindings.yml/versions/branch=latest/content#/components/messageTraits/"
                + "kafkaKeyString";
        assertEquals("/apis/registry/v3/groups/master/artifacts/kafka-bindings.yml/versions/"
                + "branch=latest/content#/components/messageTraits/kafkaKeyString",
                RegistryUrlReferenceResolver.registryReferenceName(fullReference));
    }

    @Test
    void registryReferenceNameKeepsQueryAndReturnsInputWhenNoPath() {
        assertEquals("/a/b?x=1", RegistryUrlReferenceResolver.registryReferenceName("http://h/a/b?x=1"));
        assertEquals("mailto:x@y", RegistryUrlReferenceResolver.registryReferenceName("mailto:x@y"));
        assertEquals("bad uri", RegistryUrlReferenceResolver.registryReferenceName("bad uri"));
    }

    @Test
    void decodePathSegmentDecodesEncodedValues() {
        assertEquals("master group", RegistryUrlReferenceResolver.decodePathSegment("master%20group"));
        assertEquals("branch=latest", RegistryUrlReferenceResolver.decodePathSegment("branch%3Dlatest"));
        assertEquals("a b", RegistryUrlReferenceResolver.decodePathSegment("a b"));
    }

    @Test
    void resolvesSameRegistryAndDecodesSegments() {
        RegistryUrlReferenceResolver resolver = new RegistryUrlReferenceResolver(
                "http://localhost/apis/registry/v3/");
        String ref = "http://localhost:80/apis/registry/v3/groups/master%20group/artifacts/kafka-bindings.yml/"
                + "versions/branch%3Dlatest/content";
        assertEquals(Optional.of(new ExistingArtifact(
                "/apis/registry/v3/groups/master%20group/artifacts/kafka-bindings.yml/versions/branch%3Dlatest/content",
                "master group", "kafka-bindings.yml", "branch=latest")), resolver.resolve(ref));
    }

    @Test
    void normalizesHostCaseAndDefaultPort() {
        RegistryUrlReferenceResolver resolver = new RegistryUrlReferenceResolver(
                "http://localhost/apis/registry/v3");
        assertEquals(Optional.of(new ExistingArtifact("/apis/registry/v3/groups/master/artifacts/test/versions/1",
                "master", "test", "1")),
                resolver.resolve("http://LOCALHOST:80/apis/registry/v3/groups/master/artifacts/test/versions/1"));
        RegistryUrlReferenceResolver https = new RegistryUrlReferenceResolver("https://h/apis/registry/v3");
        assertEquals("1", https.resolve("https://h:443/apis/registry/v3/groups/g/artifacts/a/versions/1")
                .orElseThrow().version());
    }

    @Test
    void rejectsDifferentRegistryAndNonMatchingPaths() {
        RegistryUrlReferenceResolver resolver = new RegistryUrlReferenceResolver(
                "http://localhost/apis/registry/v3/");
        assertEquals(Optional.empty(), resolver.resolve(
                "http://otherhost/apis/registry/v3/groups/master/artifacts/kafka-bindings.yml/versions/1"));
        assertEquals(Optional.empty(), resolver.resolve("http://localhost:8080/apis/registry/v3/groups/g/artifacts/a/versions/1"));
        assertEquals(Optional.empty(), resolver.resolve("https://localhost/apis/registry/v3/groups/g/artifacts/a/versions/1"));
        assertEquals(Optional.empty(), resolver.resolve("http://localhost/apis/registry/v3/groups/g/artifacts/a"));
        assertEquals(Optional.empty(), resolver.resolve("./avro/shared.avsc"));
        assertEquals(Optional.empty(), resolver.resolve("bad uri"));
        assertEquals(Optional.empty(), resolver.resolve(null));
        assertEquals(Optional.empty(), new RegistryUrlReferenceResolver("ftp://h/r").resolve("ftp://h/r/x"));
    }

    @Test
    void rootPathRegistryMatchesFromRoot() {
        RegistryUrlReferenceResolver resolver = new RegistryUrlReferenceResolver("http://reg/");
        assertEquals("g", resolver.resolve("http://reg/groups/g/artifacts/a/versions/1").orElseThrow().groupId());
    }
}
