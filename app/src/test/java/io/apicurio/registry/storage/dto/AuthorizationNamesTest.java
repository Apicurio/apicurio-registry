package io.apicurio.registry.storage.dto;

import io.apicurio.registry.storage.dto.AuthorizationNames.Clause;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuthorizationNamesTest {

    @Test
    void artifactNamesEscapeTheGroupOnly() {
        assertEquals("team-a/my-schema", AuthorizationNames.artifact("team-a", "my-schema"));
        assertEquals("team-a%2Fsub/x/y", AuthorizationNames.artifact("team-a/sub", "x/y"));
        assertEquals("100%25/x", AuthorizationNames.artifact("100%", "x"));
        assertEquals("default/x", AuthorizationNames.artifact(null, "x"));
        assertEquals("default/x", AuthorizationNames.artifact("default", "x"));
    }

    @Test
    void artifactNamesAreInjective() {
        assertNotEquals(AuthorizationNames.artifact("a", "b/c"), AuthorizationNames.artifact("a/b", "c"));
        assertNotEquals(AuthorizationNames.artifact("a%2Fb", "c"), AuthorizationNames.artifact("a/b", "c"));
    }

    @Test
    void groupNamesAreRaw() {
        assertEquals("team-a/sub", AuthorizationNames.group("team-a/sub"));
        assertEquals("default", AuthorizationNames.group(null));
    }

    @Test
    void exactNamesDecomposeAtTheFirstSlash() {
        assertEquals(List.of(new Clause("a/b", false, "c/d", false)), AuthorizationNames.artifactsNamed("a%2Fb/c/d"));
        assertEquals(List.of(), AuthorizationNames.artifactsNamed("no-slash"));
        assertEquals(List.of(), AuthorizationNames.artifactsNamed("bad%escape/x"));
    }

    @Test
    void prefixesDecomposeIntoGroupAndArtifactClauses() {
        assertEquals(List.of(new Clause("team-a", false, "int", true)), AuthorizationNames.artifactsWithPrefix("team-a/int"));
        assertEquals(List.of(new Clause("team", true, null, false)), AuthorizationNames.artifactsWithPrefix("team"));
        assertEquals(List.of(new Clause("a%", true, null, false), new Clause("a/", true, null, false)),
                AuthorizationNames.artifactsWithPrefix("a%2"));
        assertEquals(List.of(new Clause("a/", true, null, false)), AuthorizationNames.artifactsWithPrefix("a%2F"));
        assertEquals(List.of(), AuthorizationNames.artifactsWithPrefix("a%3"));
        assertEquals(List.of(new Clause("de", true, null, false), new Clause("default", false, null, false)),
                AuthorizationNames.artifactsWithPrefix("de"));
    }
}
