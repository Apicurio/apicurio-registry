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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end tests of {@link ReferenceRegistrar} against a running registry.
 */
@QuarkusTest
public class ReferenceRegistrarTest extends AbstractResourceTestBase {

    private static final String FIXTURES = "/io/apicurio/registry/noprofile/maven/";

    @Test
    public void registersAvroChain() throws Exception {
        String group = "refs-avro-" + UUID.randomUUID();
        SchemaSourceSet sources = SchemaSourceSet.of(
                InMemorySchemaSource.of("Exchange.avsc", fixture("Exchange.avsc"), ContentTypes.APPLICATION_JSON),
                InMemorySchemaSource.of("TradeKey.avsc", fixture("TradeKey.avsc"), ContentTypes.APPLICATION_JSON),
                InMemorySchemaSource.of("TradeRaw.avsc", fixture("TradeRaw.avsc"), ContentTypes.APPLICATION_JSON));

        RegistrationResult result = ReferenceRegistrar.register(clientV3, sources,
                RootArtifact.of("TradeRaw.avsc", group, "TradeRaw"), PlanOptions.builder().build());

        assertFalse(result.isDryRun());
        assertEquals(3, result.registered().size());
        assertEquals(1, result.roots().size());
        VersionMetaData root = result.roots().get(0);
        assertEquals(group, root.getGroupId());
        assertEquals("TradeRaw", root.getArtifactId());

        List<ArtifactReference> rootRefs = references(group, "TradeRaw", root.getVersion());
        assertEquals(1, rootRefs.size());
        assertReference(rootRefs.get(0), "com.kubetrade.schema.trade.TradeKey", "com.kubetrade.schema.trade",
                "TradeKey", "1");

        List<ArtifactReference> keyRefs = references("com.kubetrade.schema.trade", "TradeKey", "1");
        assertEquals(1, keyRefs.size());
        assertReference(keyRefs.get(0), "com.kubetrade.schema.common.Exchange", "com.kubetrade.schema.common",
                "Exchange", "1");

        assertEquals(0, references("com.kubetrade.schema.common", "Exchange", "1").size());
    }

    @Test
    public void registersProtobufChain() throws Exception {
        String group = "refs-proto-" + UUID.randomUUID();
        SchemaSourceSet sources = SchemaSourceSet.of(
                InMemorySchemaSource.of("mode/mode.proto", fixture("mode/mode.proto"), ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_info.proto", fixture("sample/table_info.proto"),
                        ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_notification_type.proto",
                        fixture("sample/table_notification_type.proto"), ContentTypes.APPLICATION_PROTOBUF),
                InMemorySchemaSource.of("sample/table_notification.proto",
                        fixture("sample/table_notification.proto"), ContentTypes.APPLICATION_PROTOBUF));
        RootArtifact rootArtifact = new RootArtifact("sample/table_notification.proto", group, "TableNotification",
                ArtifactType.PROTOBUF, ContentTypes.APPLICATION_PROTOBUF, null, null);

        RegistrationResult result = ReferenceRegistrar.register(clientV3, sources, rootArtifact,
                PlanOptions.builder().build());

        assertEquals(4, result.registered().size());
        VersionMetaData root = result.roots().get(0);
        assertEquals("TableNotification", root.getArtifactId());
        assertNotNull(root.getVersion());

        List<ArtifactReference> rootRefs = references(group, "TableNotification", root.getVersion());
        assertEquals(2, rootRefs.size());
        assertReference(rootRefs.get(0), "sample/table_info.proto", group, "sample/table_info.proto", "1");
        assertReference(rootRefs.get(1), "sample/table_notification_type.proto", group,
                "sample/table_notification_type.proto", "1");

        List<ArtifactReference> infoRefs = references(group, "sample/table_info.proto", "1");
        assertEquals(1, infoRefs.size());
        assertReference(infoRefs.get(0), "mode/mode.proto", group, "mode/mode.proto", "1");
    }

    private List<ArtifactReference> references(String groupId, String artifactId, String version) {
        List<ArtifactReference> refs = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
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
