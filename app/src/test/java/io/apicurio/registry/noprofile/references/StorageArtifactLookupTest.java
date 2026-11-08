package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.references.auto.StorageArtifactLookup;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        Assertions.assertEquals(List.of("addr"),
                lookup.searchByStructure(groupId, List.of("avro:name:Address", "avro:namespace:com.acme")));
        Assertions.assertEquals(List.of(),
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
        Assertions.assertEquals(List.of(artifactId),
                lookup.searchByStructure("default", List.of("avro:namespace:" + namespace)));
        Assertions.assertEquals(schema, lookup.latestContent("default", artifactId).get().content());
    }
}
