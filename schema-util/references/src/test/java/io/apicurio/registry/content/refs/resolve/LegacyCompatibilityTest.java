package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.refs.resolve.LegacyNamingStrategy.AvroMode;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link LegacyNamingStrategy} against the coordinates registered by the Maven plugin's auto-refs
 * support (see RegisterAsyncApiAvroAutoRefsTest and RegistryMojoWithAutoReferencesTest).
 */
class LegacyCompatibilityTest {

    private static final ArtifactTypeUtilProviderFactory FACTORY = new DefaultArtifactTypeUtilProviderImpl(true);

    private static Path fixture(String path) throws URISyntaxException {
        return Path.of(LegacyCompatibilityTest.class.getResource("/fixtures/" + path).toURI());
    }

    private static RegistrationPlan plan(Path dir, RootArtifact root, AvroMode mode) {
        PlanOptions options = PlanOptions.builder().namingStrategy(new LegacyNamingStrategy(mode, FACTORY))
                .providerFactory(FACTORY).build();
        return ReferenceResolver.plan(List.of(root),
                SchemaSourceSet.fromDirectories(List.of(dir), SchemaSourceSet.DEFAULT_FILTER), options);
    }

    private static Set<String> coordinates(RegistrationPlan plan) {
        return plan.artifacts().stream()
                .map(a -> a.coordinates().groupId() + ":" + a.coordinates().artifactId())
                .collect(Collectors.toSet());
    }

    private static Set<String> referenceNames(PlannedArtifact artifact) {
        return artifact.references().stream().map(PlannedReference::name).collect(Collectors.toSet());
    }

    @Test
    void asyncApiWithAvroInheritsParentGroup() throws Exception {
        String group = "asyncapi-avro-maven-with-references-auto";
        RootArtifact root = new RootArtifact("asyncapi-avro-refs.yml", group, "CustomersExample",
                ArtifactType.ASYNCAPI, null, null, null);
        RegistrationPlan plan = plan(fixture("examples/asyncapi-avro"), root, AvroMode.INHERIT_PARENT_GROUP);

        assertEquals(Set.of(group + ":CustomersExample", group + ":io.example.api.dtos.CustomerEvent",
                group + ":io.example.api.dtos.Address", group + ":io.example.api.dtos.PaymentMethod",
                group + ":io.example.api.dtos.PaymentMethodType",
                group + ":io.example.api.dtos.CustomerDeletedEvent"), coordinates(plan));
        assertEquals(6, plan.artifacts().size());
        PlannedArtifact rootArtifact = plan.roots().get(0);
        assertEquals("application/x-yaml", rootArtifact.contentType());
        plan.artifacts().stream().filter(a -> !a.isRoot())
                .forEach(a -> assertEquals("application/json", a.contentType()));
    }

    @Test
    void avroUsesAvroNamespaceAsGroup() throws Exception {
        RootArtifact root = new RootArtifact("TradeRaw.avsc", "com.kubetrade.schema.trade", "TradeRaw",
                ArtifactType.AVRO, null, null, null);
        RegistrationPlan plan = plan(fixture("examples/avro"), root, AvroMode.USE_AVRO_NAMESPACE);

        assertEquals(Set.of("com.kubetrade.schema.trade:TradeRaw", "com.kubetrade.schema.trade:TradeKey",
                "com.kubetrade.schema.trade:TradeValue", "com.kubetrade.schema.common:Exchange"),
                coordinates(plan));
        assertEquals(4, plan.artifacts().size());
    }

    @Test
    void protobufResolvesImportsAgainstProtoPath() throws Exception {
        String group = "autoRegisterProtoWithReferences";
        RootArtifact root = new RootArtifact("sample/table_notification.proto", group, "tableNotification",
                ArtifactType.PROTOBUF, null, null, null);
        RegistrationPlan plan = plan(fixture("maven"), root, AvroMode.INHERIT_PARENT_GROUP);

        assertEquals(4, plan.artifacts().size());
        assertEquals(Set.of(group + ":tableNotification", group + ":sample/table_info.proto",
                group + ":sample/table_notification_type.proto", group + ":mode/mode.proto"), coordinates(plan));
        PlannedArtifact rootArtifact = plan.roots().get(0);
        assertEquals(2, rootArtifact.references().size());
        assertEquals(Set.of("sample/table_info.proto", "sample/table_notification_type.proto"),
                referenceNames(rootArtifact));
    }

    @Test
    void jsonSchemaChildIdsAreRawRefs() throws Exception {
        String group = "autoRegisterJsonSchemaWithReferences";
        RootArtifact root = new RootArtifact("citizen.json", group, "citizen", ArtifactType.JSON, null, null,
                null);
        RegistrationPlan plan = plan(fixture("maven"), root, AvroMode.INHERIT_PARENT_GROUP);

        assertEquals(Set.of(group + ":citizen", group + ":city.json", group + ":citizenIdentifier.json",
                group + ":qualification/qualification.json"), coordinates(plan));
        assertEquals(3, plan.roots().get(0).references().size());
    }

    @Test
    void openApiYamlChildIdsAreRawRefs() throws Exception {
        String group = "autoRegisterOpenApiYamlWithReferences";
        RootArtifact root = new RootArtifact("petstore-api.yaml", group, "petstore-api",
                ArtifactType.OPENAPI, null, null, null);
        RegistrationPlan plan = plan(fixture("maven/openapi-yaml"), root, AvroMode.INHERIT_PARENT_GROUP);

        assertEquals(Set.of(group + ":petstore-api", group + ":./Pet.yaml", group + ":./Owner.yaml"),
                coordinates(plan));
        PlannedArtifact rootArtifact = plan.roots().get(0);
        assertEquals(Set.of("./Pet.yaml#/components/schemas/Pet"), referenceNames(rootArtifact));
    }
}
