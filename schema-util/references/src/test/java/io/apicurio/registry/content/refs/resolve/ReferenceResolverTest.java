package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceResolverTest {

    private static final String REC = "{\"type\":\"record\",\"name\":\"%s\",\"namespace\":\"%s\",\"fields\":[%s]}";

    private static SchemaSource avro(String loc, String name, String ns, String fields) {
        return InMemorySchemaSource.of(loc, REC.formatted(name, ns, fields), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void ordersDependenciesFirstAndDeduplicatesAcrossRoots() {
        SchemaSource common = avro("Common.avsc", "Common", "c", "");
        SchemaSource a = avro("A.avsc", "A", "a", "{\"name\":\"x\",\"type\":\"c.Common\"}");
        SchemaSource b = avro("B.avsc", "B", "b", "{\"name\":\"x\",\"type\":\"c.Common\"}");

        RegistrationPlan plan = ReferenceResolver.plan(
                List.of(RootArtifact.of("A.avsc", "g", "A"), RootArtifact.of("B.avsc", "g", "B")),
                SchemaSourceSet.of(common, a, b), PlanOptions.builder().build());

        assertEquals(List.of("c:Common", "g:A", "g:B"), plan.artifacts().stream()
                .map(p -> p.coordinates().groupId() + ":" + p.coordinates().artifactId()).toList());
        assertEquals("c.Common", plan.artifacts().get(1).references().get(0).name());
        assertSame(plan.artifacts().get(0), plan.artifacts().get(1).references().get(0).target());
    }

    @Test
    void cycleReportsPath() {
        SchemaSource a = avro("A.avsc", "A", "n", "{\"name\":\"x\",\"type\":\"n.B\"}");
        SchemaSource b = avro("B.avsc", "B", "n", "{\"name\":\"x\",\"type\":\"n.A\"}");
        ReferenceCycleException e = assertThrows(ReferenceCycleException.class, () -> ReferenceResolver.plan(
                List.of(RootArtifact.of("A.avsc", "g", "A")), SchemaSourceSet.of(a, b), PlanOptions.builder().build()));
        assertEquals(List.of("A.avsc", "B.avsc", "A.avsc"), e.cycle());
        assertEquals("Artifact reference loop detected: A.avsc -> B.avsc -> A.avsc", e.getMessage());
    }

    @Test
    void reportsAllUnresolvedTogether() {
        SchemaSource a = avro("A.avsc", "A", "n",
                "{\"name\":\"x\",\"type\":\"n.X\"},{\"name\":\"y\",\"type\":\"n.Y\"}");
        UnresolvedReferencesException e = assertThrows(UnresolvedReferencesException.class,
                () -> ReferenceResolver.plan(List.of(RootArtifact.of("A.avsc", "g", "A")), SchemaSourceSet.of(a),
                        PlanOptions.builder().build()));
        assertEquals(List.of(new UnresolvedReference("A.avsc", "n.X"), new UnresolvedReference("A.avsc", "n.Y")),
                e.unresolved());
        assertEquals("Unable to resolve references: n.X (from A.avsc), n.Y (from A.avsc)", e.getMessage());
    }

    @Test
    void absoluteUriIsWarningAndSameRegistryUrlIsExisting() {
        SchemaSource s = InMemorySchemaSource.of("s.json", """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","properties":{
                 "a":{"$ref":"https://other.example/x.json"},
                 "b":{"$ref":"http://reg:8080/apis/registry/v3/groups/g/artifacts/money/versions/2"}}}
                """, ContentTypes.APPLICATION_JSON);
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("s.json", "g", "s")),
                SchemaSourceSet.of(s), PlanOptions.builder()
                        .urlResolver(new RegistryUrlReferenceResolver("http://reg:8080/apis/registry/v3")).build());
        assertEquals(List.of("Skipping external reference not managed by Apicurio Registry: https://other.example/x.json"),
                plan.warnings());
        assertEquals(new ExistingArtifact("/apis/registry/v3/groups/g/artifacts/money/versions/2", "g", "money", "2"),
                plan.roots().get(0).references().get(0).target());
    }

    @Test
    void fileAndInMemorySourcesGiveSamePlan(@TempDir Path tmp) throws IOException {
        String common = REC.formatted("Common", "c", "");
        String a = REC.formatted("A", "a", "{\"name\":\"x\",\"type\":\"c.Common\"}");
        Files.writeString(tmp.resolve("Common.avsc"), common);
        Files.writeString(tmp.resolve("A.avsc"), a);
        List<RootArtifact> roots = List.of(RootArtifact.of("A.avsc", "g", "A"));
        Function<RegistrationPlan, List<String>> summary = p -> p.artifacts().stream()
                .map(x -> x.coordinates() + "|" + x.source().location()).toList();

        assertEquals(
                summary.apply(ReferenceResolver.plan(roots, SchemaSourceSet.of(
                        InMemorySchemaSource.of("Common.avsc", common, ContentTypes.APPLICATION_JSON),
                        InMemorySchemaSource.of("A.avsc", a, ContentTypes.APPLICATION_JSON)),
                        PlanOptions.builder().build())),
                summary.apply(ReferenceResolver.plan(roots,
                        SchemaSourceSet.fromDirectories(List.of(tmp), SchemaSourceSet.DEFAULT_FILTER),
                        PlanOptions.builder().build())));
    }

    @Test
    void missingRootSourceFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ReferenceResolver.plan(
                List.of(RootArtifact.of("nope.avsc", "g", "x")), SchemaSourceSet.of(), PlanOptions.builder().build()));
        assertEquals("Root schema not found: nope.avsc", e.getMessage());
    }

    @Test
    void undetectableRootTypeFails() {
        SchemaSource s = InMemorySchemaSource.of("x.unknown", "???", "text/plain");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ReferenceResolver.plan(
                List.of(RootArtifact.of("x.unknown", "g", "x")), SchemaSourceSet.of(s),
                PlanOptions.builder().providerFactory(new DefaultArtifactTypeUtilProviderImpl(true)).build()));
        assertEquals("Unable to detect artifact type of x.unknown", e.getMessage());
    }

    @Test
    void rootOverridesTypeAndContentTypeAndKeepsRootMetadata() {
        SchemaSource s = avro("A.avsc", "A", "a", "");
        RootArtifact root = new RootArtifact("A.avsc", "g", "A", ArtifactType.AVRO, "application/x-custom", "3",
                Boolean.TRUE);
        RegistrationPlan plan = ReferenceResolver.plan(List.of(root), SchemaSourceSet.of(s),
                PlanOptions.builder().build());
        PlannedArtifact p = plan.artifacts().get(0);
        assertEquals(ArtifactType.AVRO, p.artifactType());
        assertEquals("application/x-custom", p.contentType());
        assertSame(root, p.root());
        assertTrue(p.isRoot());
        assertEquals(List.of(), p.references());
        assertEquals("PlannedArtifact[ArtifactCoordinates[groupId=g, artifactId=A], A.avsc]", p.toString());
    }

    @Test
    void childUsesSourceContentTypeAndNamingStrategy() {
        SchemaSource common = avro("Common.avsc", "Common", "c", "");
        SchemaSource a = avro("A.avsc", "A", "a", "{\"name\":\"x\",\"type\":\"c.Common\"}");
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("A.avsc", "g", "A")),
                SchemaSourceSet.of(common, a), PlanOptions.builder()
                        .namingStrategy(ctx -> new ArtifactCoordinates(ctx.parent().groupId() + "-child",
                                ctx.resource() + "|" + ctx.parentArtifactType()))
                        .build());
        PlannedArtifact child = plan.artifacts().get(0);
        assertEquals(new ArtifactCoordinates("g-child", "c.Common|AVRO"), child.coordinates());
        assertEquals(ContentTypes.APPLICATION_JSON, child.contentType());
        assertFalse(child.isRoot());
        assertNull(child.root());
        assertEquals(List.of(plan.artifacts().get(1)), plan.roots());
    }

    @Test
    void existingArtifactIsReferencedNotPlanned() {
        SchemaSource a = avro("A.avsc", "A", "a", "{\"name\":\"x\",\"type\":\"c.Common\"}");
        ExistingArtifact existing = new ExistingArtifact("c.Common", "c", "Common", "7");
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("A.avsc", "g", "A")),
                SchemaSourceSet.of(a), PlanOptions.builder().existingArtifacts(List.of(existing)).build());
        assertEquals(1, plan.artifacts().size());
        assertEquals(List.of(new PlannedReference("c.Common", existing)), plan.artifacts().get(0).references());
    }

    @Test
    void rootCoordinatesWinWhenAlsoReferencedAsChild() {
        SchemaSource common = avro("Common.avsc", "Common", "c", "");
        SchemaSource a = avro("A.avsc", "A", "a", "{\"name\":\"x\",\"type\":\"c.Common\"}");
        RegistrationPlan plan = ReferenceResolver.plan(
                List.of(RootArtifact.of("A.avsc", "g", "A"), RootArtifact.of("Common.avsc", "g", "MyCommon")),
                SchemaSourceSet.of(common, a), PlanOptions.builder().build());
        assertEquals(2, plan.artifacts().size());
        assertEquals(new ArtifactCoordinates("g", "MyCommon"), plan.artifacts().get(0).coordinates());
        assertTrue(plan.artifacts().get(0).isRoot());
        assertEquals(2, plan.roots().size());
    }

    @Test
    void urlResolverMissFallsBackToWarningAndRelativeUnresolved() {
        SchemaSource s = InMemorySchemaSource.of("s.json", """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","properties":{
                 "a":{"$ref":"https://other.example/x.json"},
                 "b":{"$ref":"#/definitions/local"},
                 "c":{"$ref":"missing.json"}}}
                """, ContentTypes.APPLICATION_JSON);
        UnresolvedReferencesException e = assertThrows(UnresolvedReferencesException.class,
                () -> ReferenceResolver.plan(List.of(RootArtifact.of("s.json", "g", "s")), SchemaSourceSet.of(s),
                        PlanOptions.builder().urlResolver(ref -> Optional.empty()).build()));
        assertEquals(List.of(new UnresolvedReference("s.json", "missing.json")), e.unresolved());
    }

    @Test
    void nullResourceReferencesAreSkipped() {
        SchemaSource s = avro("A.avsc", "A", "a", "");
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("A.avsc", "g", "A")),
                SchemaSourceSet.of(s), PlanOptions.builder().providerFactory(factoryWithFinder(content ->
                        Set.of(new ExternalReference("#/local", null, "/local")))).build());
        assertEquals(List.of(), plan.artifacts().get(0).references());
    }

    @Test
    void referenceFinderFailureNamesLocation() {
        SchemaSource s = avro("A.avsc", "A", "a", "");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ReferenceResolver.plan(
                List.of(RootArtifact.of("A.avsc", "g", "A")), SchemaSourceSet.of(s),
                PlanOptions.builder().providerFactory(factoryWithFinder(content -> {
                    throw new IllegalStateException("boom");
                })).build()));
        assertEquals("Unable to find references in A.avsc: boom", e.getMessage());
        assertEquals("boom", e.getCause().getMessage());
    }

    private static DefaultArtifactTypeUtilProviderImpl factoryWithFinder(
            Function<TypedContent, Set<ExternalReference>> finder) {
        return new DefaultArtifactTypeUtilProviderImpl(true) {
            @Override
            public ArtifactTypeUtilProvider getArtifactTypeProvider(String type) {
                ArtifactTypeUtilProvider real = super.getArtifactTypeProvider(type);
                return (ArtifactTypeUtilProvider) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] { ArtifactTypeUtilProvider.class }, (proxy, method, args) -> {
                            if ("getReferenceFinder".equals(method.getName())) {
                                return (ReferenceFinder) finder::apply;
                            }
                            return method.invoke(real, args);
                        });
            }
        };
    }
}
