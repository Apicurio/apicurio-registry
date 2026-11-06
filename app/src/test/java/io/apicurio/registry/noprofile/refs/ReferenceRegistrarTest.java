package io.apicurio.registry.noprofile.refs;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.refs.ReferenceRegistrar;
import io.apicurio.registry.client.refs.RegistrationResult;
import io.apicurio.registry.content.refs.resolve.InMemorySchemaSource;
import io.apicurio.registry.content.refs.resolve.PlanOptions;
import io.apicurio.registry.content.refs.resolve.RootArtifact;
import io.apicurio.registry.content.refs.resolve.SchemaSourceSet;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end tests of {@link ReferenceRegistrar} against a running registry.
 */
@QuarkusTest
public class ReferenceRegistrarTest extends AbstractResourceTestBase {

    private static final String FIXTURES = "/io/apicurio/registry/noprofile/maven/";

    @Test
    public void registersAvroChain() throws Exception {
        String group = "refs-avro-" + UUID.randomUUID();
        String ns = "com.kubetrade.t" + UUID.randomUUID().toString().replace("-", "");
        String tradeNs = ns + ".trade";
        String commonNs = ns + ".common";
        SchemaSourceSet sources = SchemaSourceSet.of(
                InMemorySchemaSource.of("Exchange.avsc", avroFixture("Exchange.avsc", ns),
                        ContentTypes.APPLICATION_JSON),
                InMemorySchemaSource.of("TradeKey.avsc", avroFixture("TradeKey.avsc", ns),
                        ContentTypes.APPLICATION_JSON),
                InMemorySchemaSource.of("TradeRaw.avsc", avroFixture("TradeRaw.avsc", ns),
                        ContentTypes.APPLICATION_JSON));

        RegistrationResult result = ReferenceRegistrar.register(clientV3, sources,
                RootArtifact.of("TradeRaw.avsc", group, "TradeRaw"), PlanOptions.builder().build());

        assertFalse(result.isDryRun());
        assertEquals(3, result.registered().size());
        assertEquals(1, result.roots().size());
        VersionMetaData root = result.roots().get(0);
        assertEquals(group, root.getGroupId());
        assertEquals("TradeRaw", root.getArtifactId());
        assertEquals(versionOf(result, group, "TradeRaw"), root.getVersion());

        List<ArtifactReference> rootRefs = references(group, "TradeRaw", root.getVersion());
        assertEquals(1, rootRefs.size());
        assertReference(rootRefs.get(0), tradeNs + ".TradeKey", tradeNs, "TradeKey",
                versionOf(result, tradeNs, "TradeKey"));

        String tradeKeyVersion = versionOf(result, tradeNs, "TradeKey");
        List<ArtifactReference> keyRefs = references(tradeNs, "TradeKey", tradeKeyVersion);
        assertEquals(1, keyRefs.size());
        assertReference(keyRefs.get(0), commonNs + ".Exchange", commonNs, "Exchange",
                versionOf(result, commonNs, "Exchange"));

        assertEquals(0, references(commonNs, "Exchange", versionOf(result, commonNs, "Exchange")).size());
    }

    @Test
    public void registersProtobufChain() throws Exception {
        String group = "refs-proto-" + UUID.randomUUID();
        SchemaSourceSet sources = SchemaSourceSet.of(
                InMemorySchemaSource.of("mode/mode.proto", fixture("mode/mode.proto"),
                        ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_info.proto", fixture("sample/table_info.proto"),
                        ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_notification_type.proto",
                        fixture("sample/table_notification_type.proto"), ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_notification.proto",
                        fixture("sample/table_notification.proto"), ContentTypes.APPLICATION_PROTOBUF));
        RootArtifact rootArtifact = new RootArtifact("sample/table_notification.proto", group,
                "TableNotification", ArtifactType.PROTOBUF, ContentTypes.APPLICATION_PROTOBUF, null, null);

        RegistrationResult result = ReferenceRegistrar.register(clientV3, sources, rootArtifact,
                PlanOptions.builder().build());

        assertEquals(4, result.registered().size());
        assertEquals(1, result.roots().size());
        VersionMetaData root = result.roots().get(0);
        assertEquals(group, root.getGroupId());
        assertEquals("TableNotification", root.getArtifactId());
        assertEquals(versionOf(result, group, "TableNotification"), root.getVersion());

        List<ArtifactReference> rootRefs = references(group, "TableNotification", root.getVersion());
        assertEquals(2, rootRefs.size());
        assertReference(rootRefs.get(0), "sample/table_info.proto", group, "sample/table_info.proto",
                versionOf(result, group, "sample/table_info.proto"));
        assertReference(rootRefs.get(1), "sample/table_notification_type.proto", group,
                "sample/table_notification_type.proto",
                versionOf(result, group, "sample/table_notification_type.proto"));

        List<ArtifactReference> infoRefs = references(group, "sample/table_info.proto",
                versionOf(result, group, "sample/table_info.proto"));
        assertEquals(1, infoRefs.size());
        assertReference(infoRefs.get(0), "mode/mode.proto", group, "mode/mode.proto",
                versionOf(result, group, "mode/mode.proto"));
    }

    private static String versionOf(RegistrationResult result, String groupId, String artifactId) {
        List<VersionMetaData> matches = result.registered().values().stream()
                .filter(v -> groupId.equals(v.getGroupId()) && artifactId.equals(v.getArtifactId())).toList();
        assertEquals(1, matches.size(), "registered entries for " + groupId + "/" + artifactId);
        return matches.get(0).getVersion();
    }

    private static String avroFixture(String path, String namespace) throws IOException {
        return fixture(path).replace("com.kubetrade.schema", namespace);
    }

    private List<ArtifactReference> references(String groupId, String artifactId, String version) {
        List<ArtifactReference> refs = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId)
                .versions().byVersionExpression(version).references().get();
        return refs.stream().sorted(Comparator.comparing(ArtifactReference::getName)).toList();
    }

    private static void assertReference(ArtifactReference ref, String name, String groupId, String artifactId,
            String version) {
        assertEquals(name, ref.getName());
        assertEquals(groupId, ref.getGroupId());
        assertEquals(artifactId, ref.getArtifactId());
        assertEquals(version, ref.getVersion());
    }

    private static String fixture(String path) throws IOException {
        try (InputStream in = ReferenceRegistrarTest.class.getResourceAsStream(FIXTURES + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
