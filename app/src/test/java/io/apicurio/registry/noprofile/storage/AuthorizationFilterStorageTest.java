package io.apicurio.registry.noprofile.storage;

import io.apicurio.authz.SearchFilterData;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the storage translation of {@link AuthorizationFilter} (per-resource authorization
 * search pre-filtering) against the reference pattern semantics, using names that are easy to get
 * wrong in SQL: group IDs containing '/', the default group, LIKE metacharacters and the LIKE
 * escape character, and near-miss prefixes.
 */
@QuarkusTest
public class AuthorizationFilterStorageTest extends AbstractResourceTestBase {

    private static final String P = "azf" + UUID.randomUUID().toString().substring(0, 8);
    private static final String DEFAULT = "default";

    /** group (null = default group) -> artifact IDs */
    private static final Map<String, List<String>> ARTIFACTS = new LinkedHashMap<>();
    private static final String OWNED_GROUP = P + "o";
    private static final String OWNER = "carol";

    @Inject
    @Current
    RegistryStorage storage;

    private RegistryStorage getStorage() {
        return storage;
    }

    static {
        ARTIFACTS.put(P + "a", List.of("x", "secret-1", "b/c"));
        ARTIFACTS.put(P + "a/b", List.of("c"));
        ARTIFACTS.put(P + "ab", List.of("x"));
        ARTIFACTS.put(P + "_", List.of("x"));
        ARTIFACTS.put(P + "%", List.of("x"));
        ARTIFACTS.put(P + "!", List.of("x"));
        ARTIFACTS.put(OWNED_GROUP, List.of("owned"));
        ARTIFACTS.put(null, List.of(P + "d1"));
    }

    private boolean dataCreated;

    /** Runs per test (storage needs the request context), but creates the data only once. */
    @BeforeEach
    void createData() {
        if (dataCreated) {
            return;
        }
        dataCreated = true;
        int i = 0;
        for (Map.Entry<String, List<String>> e : ARTIFACTS.entrySet()) {
            String group = e.getKey();
            String owner = OWNED_GROUP.equals(group) ? OWNER : "admin";
            if (group != null) {
                try {
                    getStorage().createGroup(GroupMetaDataDto.builder().groupId(group).owner(owner)
                            .createdOn(System.currentTimeMillis()).build());
                } catch (GroupAlreadyExistsException ignored) {
                    // created by a previous run of this class in the same JVM
                }
            }
            for (String artifactId : e.getValue()) {
                ContentWrapperDto content = ContentWrapperDto.builder()
                        .content(ContentHandle.create("{\"n\":\"" + P + (i++) + "\"}"))
                        .contentType(ContentTypes.APPLICATION_JSON).build();
                getStorage().createArtifact(group, artifactId, ArtifactType.JSON,
                        EditableArtifactMetaDataDto.builder().build(), null, content,
                        EditableVersionMetaDataDto.builder().build(), List.of(), false, false, owner);
            }
        }
    }

    /**
     * Identifies an artifact by its (group, artifact) pair. Two different artifacts can share a
     * grants resource name (group "a" + artifact "b/c" vs group "a/b" + artifact "c"), so results
     * are compared by key, not by grants name.
     */
    private static String key(String group, String artifactId) {
        return (group == null ? DEFAULT : group) + " :: " + artifactId;
    }

    private static String grantsName(String key) {
        return key.replace(" :: ", "/");
    }

    /** All artifacts of this test, as keys. */
    private static Set<String> allKeys() {
        Set<String> keys = new HashSet<>();
        ARTIFACTS.forEach((g, ids) -> ids.forEach(a -> keys.add(key(g, a))));
        return keys;
    }

    private static boolean ownedByCarol(String key) {
        return key.startsWith(OWNED_GROUP + " :: ");
    }

    private Set<String> searchArtifacts(AuthorizationFilter filter) {
        return getStorage().searchArtifacts(Set.of(SearchFilter.ofAuthorization(filter)), OrderBy.name,
                OrderDirection.asc, 0, 10000, false).getArtifacts().stream()
                .map(a -> key(a.getGroupId(), a.getArtifactId()))
                .filter(allKeys()::contains)
                .collect(Collectors.toSet());
    }

    private Set<String> searchVersions(AuthorizationFilter filter) {
        return getStorage().searchVersions(Set.of(SearchFilter.ofAuthorization(filter)), OrderBy.name,
                OrderDirection.asc, 0, 10000, false).getVersions().stream()
                .map(v -> key(v.getGroupId(), v.getArtifactId()))
                .filter(allKeys()::contains)
                .collect(Collectors.toSet());
    }

    private void assertArtifactsMatchReference(AuthorizationFilter filter) {
        SearchFilterData reference = new SearchFilterData(filter.allowAll(), filter.allowExact(),
                filter.allowPrefix(), filter.denyExact(), filter.denyPrefix());
        Set<String> expected = allKeys().stream()
                .filter(k -> reference.matches(grantsName(k)) || (OWNER.equals(filter.owner()) && ownedByCarol(k)))
                .collect(Collectors.toSet());

        assertEquals(expected, searchArtifacts(filter), "artifacts for " + filter);
        assertEquals(expected, searchVersions(filter), "versions for " + filter);
    }

    private static AuthorizationFilter allow(Set<String> exact, Set<String> prefix) {
        return new AuthorizationFilter(false, exact, prefix, Set.of(), Set.of(), null);
    }

    @Test
    void groupPrefixIncludesGroupsWhoseIdContainsSlash() {
        AuthorizationFilter filter = allow(Set.of(), Set.of(P + "a/"));

        assertArtifactsMatchReference(filter);
        assertEquals(Set.of(key(P + "a", "x"), key(P + "a", "secret-1"), key(P + "a", "b/c"),
                key(P + "a/b", "c")), searchArtifacts(filter));
    }

    @Test
    void exactNameMatchesEverySplitAtSlash() {
        AuthorizationFilter filter = allow(Set.of(P + "a/b/c"), Set.of());

        assertArtifactsMatchReference(filter);
        assertEquals(Set.of(key(P + "a", "b/c"), key(P + "a/b", "c")), searchArtifacts(filter));
    }

    @Test
    void prefixWithoutSlashMatchesGroupPrefix() {
        assertArtifactsMatchReference(allow(Set.of(), Set.of(P + "a")));
    }

    @Test
    void likeMetacharactersAndEscapeCharacterMatchLiterally() {
        assertArtifactsMatchReference(allow(Set.of(), Set.of(P + "_")));
        assertArtifactsMatchReference(allow(Set.of(), Set.of(P + "%")));
        assertArtifactsMatchReference(allow(Set.of(), Set.of(P + "!")));
        assertEquals(Set.of(key(P + "_", "x")), searchArtifacts(allow(Set.of(), Set.of(P + "_"))));
    }

    @Test
    void defaultGroupIsNamedDefault() {
        assertArtifactsMatchReference(allow(Set.of("default/" + P + "d1"), Set.of()));
        assertArtifactsMatchReference(allow(Set.of(), Set.of("default/" + P)));
        assertArtifactsMatchReference(allow(Set.of(), Set.of("def")));
        assertEquals(Set.of(key(null, P + "d1")), searchArtifacts(allow(Set.of(), Set.of("default/" + P))));
    }

    @Test
    void denyRulesApplyOnTopOfAllowAll() {
        assertArtifactsMatchReference(new AuthorizationFilter(true, Set.of(), Set.of(),
                Set.of(P + "ab/x"), Set.of(P + "a/secret"), null));
    }

    @Test
    void denyRulesApplyOnTopOfPrefixAllow() {
        assertArtifactsMatchReference(new AuthorizationFilter(false, Set.of(), Set.of(P),
                Set.of(), Set.of(P + "a/b", P + "%"), null));
    }

    @Test
    void ownerSeesOwnedResourcesWithoutGrants() {
        AuthorizationFilter filter = new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(),
                OWNER);

        assertArtifactsMatchReference(filter);
        assertEquals(Set.of(key(OWNED_GROUP, "owned")), searchArtifacts(filter));
    }

    @Test
    void nothingAllowedMatchesNothing() {
        assertEquals(Set.of(), searchArtifacts(allow(Set.of(), Set.of())));
    }

    @Test
    void groupSearchUsesGroupNames() {
        Set<String> groups = ARTIFACTS.keySet().stream().filter(g -> g != null).collect(Collectors.toSet());
        AuthorizationFilter filter = new AuthorizationFilter(false, Set.of(P + "ab"), Set.of(P + "a/"),
                Set.of(), Set.of(), OWNER);

        Set<String> found = getStorage().searchGroups(Set.of(SearchFilter.ofAuthorization(filter)),
                OrderBy.groupId, OrderDirection.asc, 0, 10000).getGroups().stream()
                .map(g -> g.getId()).filter(groups::contains).collect(Collectors.toSet());

        assertEquals(Set.of(P + "ab", P + "a/b", OWNED_GROUP), found);
    }
}
