package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.references.auto.StorageArtifactLookup;
import io.apicurio.registry.references.auto.StructureSearchResult;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@QuarkusTest
public class StorageArtifactLookupTest extends AbstractResourceTestBase {

    private static final String ADDRESS_V1 = "{\"type\":\"record\",\"name\":\"Address\",\"namespace\":\"com.acme\","
            + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"}]}";
    private static final String ADDRESS_V2 = "{\"type\":\"record\",\"name\":\"Address\",\"namespace\":\"com.acme\","
            + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"},{\"name\":\"city\",\"type\":\"string\"}]}";

    @Inject
    StorageArtifactLookup lookup;

    @Test
    public void testLookupInNonDefaultGroup() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "addr", ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON);

        Assertions.assertEquals(Optional.of("1"), lookup.latestVersion(groupId, "addr"));
        Assertions.assertEquals(Optional.empty(), lookup.latestVersion(groupId, "missing"));
        Assertions.assertEquals(Optional.empty(), lookup.latestContent(groupId, "missing"));
        Assertions.assertEquals(StructureSearchResult.complete(List.of("addr")),
                lookup.searchByStructure(groupId, List.of("avro:name:Address", "avro:namespace:com.acme")));
        Assertions.assertEquals(StructureSearchResult.complete(List.of()),
                lookup.searchByStructure(groupId, List.of("avro:name:Other")));

        Optional<ContentHandle> content = lookup.latestContent(groupId, "addr");
        Assertions.assertTrue(content.isPresent());
        Assertions.assertEquals(ADDRESS_V1, content.get().content());

        createArtifactVersion(groupId, "addr", ADDRESS_V2, ContentTypes.APPLICATION_JSON);
        Assertions.assertEquals(Optional.of("2"), lookup.latestVersion(groupId, "addr"));
        Assertions.assertEquals(ADDRESS_V2, lookup.latestContent(groupId, "addr").get().content());
    }

    @Test
    public void testLookupInDefaultGroup() throws Exception {
        String namespace = "com.acme.n" + UUID.randomUUID().toString().replace("-", "");
        String artifactId = "addr-" + UUID.randomUUID();
        String schema = "{\"type\":\"record\",\"name\":\"Address\",\"namespace\":\"" + namespace + "\","
                + "\"fields\":[{\"name\":\"street\",\"type\":\"string\"}]}";
        createArtifact("default", artifactId, ArtifactType.AVRO, schema, ContentTypes.APPLICATION_JSON);

        Assertions.assertEquals(Optional.of("1"), lookup.latestVersion("default", artifactId));
        Assertions.assertEquals(StructureSearchResult.complete(List.of(artifactId)),
                lookup.searchByStructure("default", List.of("avro:namespace:" + namespace)));
        Assertions.assertEquals(schema, lookup.latestContent("default", artifactId).get().content());
    }

    @Test
    public void testSearchByStructureMatchesGroupExactly() throws Exception {
        String prefix = UUID.randomUUID().toString();
        String wildcardGroup = prefix + "-a*";
        String otherGroup = prefix + "-ab";
        createArtifact(wildcardGroup, "addr-star", ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON);
        createArtifact(otherGroup, "addr-other", ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON);

        Assertions.assertEquals(StructureSearchResult.complete(List.of("addr-star")),
                lookup.searchByStructure(wildcardGroup, List.of("avro:name:Address", "avro:namespace:com.acme")));
        Assertions.assertEquals(StructureSearchResult.complete(List.of("addr-other")),
                lookup.searchByStructure(otherGroup, List.of("avro:name:Address", "avro:namespace:com.acme")));
    }

    @Test
    public void testSearchByStructurePagesAndReportsTruncation() throws Exception {
        String groupId = TestUtils.generateGroupId();
        List<String> filters = List.of("avro:name:Address", "avro:namespace:com.acme");
        // Exactly one full page: every candidate is returned and the result is complete.
        List<String> expected = IntStream.range(0, StorageArtifactLookup.MAX_CANDIDATES)
                .mapToObj(i -> String.format(Locale.ROOT, "addr-%03d", i)).toList();
        for (String artifactId : expected) {
            createArtifact(groupId, artifactId, ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON);
        }
        Assertions.assertEquals(StructureSearchResult.complete(expected),
                lookup.searchByStructure(groupId, filters));

        // One more candidate than can be confirmed: the result is flagged as truncated.
        String extra = String.format(Locale.ROOT, "addr-%03d", StorageArtifactLookup.MAX_CANDIDATES);
        createArtifact(groupId, extra, ArtifactType.AVRO, ADDRESS_V1, ContentTypes.APPLICATION_JSON);
        StructureSearchResult result = lookup.searchByStructure(groupId, filters);
        Assertions.assertTrue(result.truncated());
    }
}
