package io.apicurio.registry.references.auto;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ReferenceMatcher}.
 */
class ReferenceMatcherTest {

    /** In-memory ArtifactLookup: artifacts keyed "g/a" with version, content and structure entries. */
    static final class FakeLookup implements ArtifactLookup {
        final Map<String, String> versions = new HashMap<>();
        final Map<String, String> contents = new HashMap<>();
        final Map<String, Set<String>> structure = new HashMap<>();
        boolean truncated;

        FakeLookup add(String g, String a, String version, String content, String... structureEntries) {
            versions.put(g + "/" + a, version);
            contents.put(g + "/" + a, content);
            structure.put(g + "/" + a, Set.of(structureEntries));
            return this;
        }

        @Override
        public Optional<String> latestVersion(String g, String a) {
            return Optional.ofNullable(versions.get(g + "/" + a));
        }

        @Override
        public StructureSearchResult searchByStructure(String g, List<String> filters) {
            List<String> ids = structure.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(g + "/") && e.getValue().containsAll(filters))
                    .map(e -> e.getKey().substring(g.length() + 1)).sorted().toList();
            return new StructureSearchResult(ids, truncated);
        }

        @Override
        public Optional<ContentHandle> latestContent(String g, String a) {
            return Optional.ofNullable(contents.get(g + "/" + a)).map(ContentHandle::create);
        }
    }

    private static Set<ExternalReference> refs(String... names) {
        return Arrays.stream(names).map(ExternalReference::new).collect(Collectors.toSet());
    }

    private static String avro(String ns, String name) {
        return "{\"type\":\"record\",\"namespace\":\"" + ns + "\",\"name\":\"" + name + "\",\"fields\":[]}";
    }

    @Test
    void avroMatchesFullNameArtifactIdInGroup() {
        FakeLookup lookup = new FakeLookup().add("orders", "com.acme.Address", "3", avro("com.acme", "Address"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals(List.of(ArtifactReferenceDto.builder().name("com.acme.Address").groupId("orders")
                .artifactId("com.acme.Address").version("3").build()), m.matched());
        assertEquals(List.of(), m.problems());
    }

    @Test
    void avroMatchesNamespaceGroupConvention() {
        FakeLookup lookup = new FakeLookup().add("com.acme", "Address", "1", avro("com.acme", "Address"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals("com.acme", m.matched().get(0).getGroupId());
        assertEquals("Address", m.matched().get(0).getArtifactId());
        assertEquals("1", m.matched().get(0).getVersion());
    }

    @Test
    void avroFallsBackToStructureIndexAndConfirmsFullName() {
        FakeLookup lookup = new FakeLookup()
                .add("orders", "addr", "2", avro("com.acme", "Address"), "avro:name:Address", "avro:namespace:com.acme")
                .add("orders", "other", "5", avro("com.other", "Address"), "avro:name:Address", "avro:namespace:com.acme");
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals(List.of(ArtifactReferenceDto.builder().name("com.acme.Address").groupId("orders")
                .artifactId("addr").version("2").build()), m.matched());
    }

    @Test
    void ambiguousStructureMatchIsReported() {
        FakeLookup lookup = new FakeLookup()
                .add("orders", "a1", "1", avro("com.acme", "Address"), "avro:name:Address", "avro:namespace:com.acme")
                .add("orders", "a2", "1", avro("com.acme", "Address"), "avro:name:Address", "avro:namespace:com.acme");
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals(List.of(new MatchProblem("com.acme.Address",
                "Ambiguous: matches orders/a1, orders/a2.")), m.problems());
        assertEquals(List.of(), m.matched());
    }

    @Test
    void protobufPathMatchesNormalizedThenRawArtifactId() {
        FakeLookup lookup = new FakeLookup().add("g", "./acme/legacy.proto", "4", "x");
        lookup.add("g", "acme/address.proto", "1", "x");
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.PROTOBUF,
                refs("./acme/address.proto", "./acme/legacy.proto"), Set.of());
        assertEquals(List.of("acme/address.proto", "./acme/legacy.proto"),
                m.matched().stream().map(ArtifactReferenceDto::getArtifactId).toList());
    }

    @Test
    void jsonAbsoluteIdUsesIndex() {
        FakeLookup lookup = new FakeLookup().add("g", "money", "7", "{}", "json:id:https://ex.com/money.json");
        Set<ExternalReference> r = Set.of(new JsonPointerExternalReference("https://ex.com/money.json#/defs/M"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.JSON, r, Set.of());
        assertEquals("money", m.matched().get(0).getArtifactId());
        assertEquals("https://ex.com/money.json#/defs/M", m.matched().get(0).getName());
    }

    @Test
    void notFoundListsAllSortedAndExcludedNamesAreSkipped() {
        ReferenceMatch m = new ReferenceMatcher(new FakeLookup()).match("orders", ArtifactType.AVRO,
                refs("z.Z", "a.A", "m.M"), Set.of("m.M"));
        assertEquals(List.of(
                new MatchProblem("a.A", "No matching artifact found in group 'orders'."),
                new MatchProblem("z.Z", "No matching artifact found in group 'orders'.")), m.problems());
    }

    @Test
    void artifactWithoutLatestVersionIsNotFound() {
        FakeLookup lookup = new FakeLookup();
        lookup.contents.put("g/x.proto", "x");
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.PROTOBUF, refs("x.proto"), Set.of());
        assertEquals(List.of(new MatchProblem("x.proto", "No matching artifact found in group 'g'.")),
                m.problems());
    }

    @Test
    void explicitResourceNameExcludesFragmentReferences() {
        FakeLookup lookup = new FakeLookup().add("g", "money.json", "1", "{}")
                .add("g", "other.json", "2", "{}");
        Set<ExternalReference> r = Set.of(new JsonPointerExternalReference("money.json#/defs/A"),
                new JsonPointerExternalReference("money.json#/defs/B"),
                new JsonPointerExternalReference("other.json#/defs/C"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.JSON, r, Set.of("money.json"));
        assertEquals(List.of(ArtifactReferenceDto.builder().name("other.json#/defs/C").groupId("g")
                .artifactId("other.json").version("2").build()), m.matched());
        assertEquals(List.of(), m.problems());
    }

    @Test
    void explicitFragmentNameExcludesOtherReferencesToSameResource() {
        FakeLookup lookup = new FakeLookup().add("g", "money.json", "1", "{}");
        Set<ExternalReference> r = Set.of(new JsonPointerExternalReference("money.json#/defs/A"),
                new JsonPointerExternalReference("money.json#/defs/B"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.JSON, r,
                Set.of("money.json#/defs/A"));
        assertEquals(List.of(), m.matched());
        assertEquals(List.of(), m.problems());
    }

    @Test
    void truncatedAvroSearchIsReportedInsteadOfPickingACandidate() {
        FakeLookup lookup = new FakeLookup()
                .add("orders", "addr", "2", avro("com.acme", "Address"), "avro:name:Address", "avro:namespace:com.acme");
        lookup.truncated = true;
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals(List.of(), m.matched());
        assertEquals(List.of(new MatchProblem("com.acme.Address", "Too many candidate artifacts in group 'orders'"
                + " to match unambiguously. Provide an explicit reference.")), m.problems());
    }

    @Test
    void truncatedSearchWithoutConfirmedCandidateIsStillReported() {
        FakeLookup lookup = new FakeLookup();
        lookup.truncated = true;
        ReferenceMatch m = new ReferenceMatcher(lookup).match("orders", ArtifactType.AVRO,
                refs("com.acme.Address"), Set.of());
        assertEquals(List.of(new MatchProblem("com.acme.Address", "Too many candidate artifacts in group 'orders'"
                + " to match unambiguously. Provide an explicit reference.")), m.problems());
    }

    @Test
    void truncatedJsonIdSearchIsReportedInsteadOfPickingACandidate() {
        FakeLookup lookup = new FakeLookup().add("g", "money", "7", "{}", "json:id:https://ex.com/money.json");
        lookup.truncated = true;
        Set<ExternalReference> r = Set.of(new JsonPointerExternalReference("https://ex.com/money.json"));
        ReferenceMatch m = new ReferenceMatcher(lookup).match("g", ArtifactType.JSON, r, Set.of());
        assertEquals(List.of(), m.matched());
        assertEquals(List.of(new MatchProblem("https://ex.com/money.json", "Too many candidate artifacts in group"
                + " 'g' to match unambiguously. Provide an explicit reference.")), m.problems());
    }

    @Test
    void exceptionCarriesSortedCauses() {
        ReferenceAutoMatchException e = new ReferenceAutoMatchException(List.of(
                new MatchProblem("z", "No matching artifact found in group 'g'."),
                new MatchProblem("a", "Ambiguous: matches g/x, g/y.")));
        assertEquals(ReferenceAutoMatchException.TITLE, e.getMessage());
        assertEquals(RuleType.INTEGRITY, e.getRuleType());
        assertEquals(List.of("a", "z"), e.getCauses().stream().map(RuleViolation::getContext).toList());
    }
}
