package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.references.auto.ReferenceAutoMatchException;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * REST tests for server-side matching of detected references to existing artifacts
 * ({@code autoReferences}).
 */
@QuarkusTest
public class AutoReferencesTest extends AbstractResourceTestBase {

    private static final String ADDRESS = """
            {"type":"record","namespace":"com.acme","name":"Address","fields":[]}""";

    @Test
    public void avroMatchedInSameGroup() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);

        createAuto(g, "Order", ArtifactType.AVRO, order("com.acme", "com.acme.Address"));

        assertRefs(g, "Order", "1", List.of(ref("com.acme.Address", g, "com.acme.Address", "1")));
    }

    @Test
    public void avroMatchedInDefaultGroup() throws Exception {
        String ns = "com.acme.d" + UUID.randomUUID().toString().replace("-", "");
        createArtifact("default", ns + ".Address", ArtifactType.AVRO, avroRecord(ns, "Address"),
                ContentTypes.APPLICATION_JSON);

        createAuto("default", ns + ".Order", ArtifactType.AVRO, order(ns, ns + ".Address"));

        assertRefs("default", ns + ".Order", "1", List.of(ref(ns + ".Address", null, ns + ".Address", "1")));
    }

    @Test
    public void avroMatchedInNamespaceGroup() throws Exception {
        String g = newGroup();
        String ns = "com.acme.u" + UUID.randomUUID().toString().replace("-", "");
        createArtifact(ns, "Address", ArtifactType.AVRO, avroRecord(ns, "Address"),
                ContentTypes.APPLICATION_JSON);

        createAuto(g, "Order", ArtifactType.AVRO, order("com.acme", ns + ".Address"));

        assertRefs(g, "Order", "1", List.of(ref(ns + ".Address", ns, "Address", "1")));
    }

    @Test
    public void protobufImportMatchedByPath() throws Exception {
        String g = newGroup();
        createArtifact(g, "acme/address.proto", ArtifactType.PROTOBUF,
                "syntax = \"proto3\";\npackage acme;\nmessage Address { string street = 1; }\n",
                ContentTypes.APPLICATION_PROTOBUF);

        createAuto(g, "order.proto", ArtifactType.PROTOBUF, """
                syntax = "proto3";
                package shop;
                import "acme/address.proto";
                message Order { acme.Address address = 1; }
                """, ContentTypes.APPLICATION_PROTOBUF);

        assertRefs(g, "order.proto", "1",
                List.of(ref("acme/address.proto", g, "acme/address.proto", "1")));
    }

    @Test
    public void jsonAbsoluteIdMatchedThroughIndex() throws Exception {
        String g = newGroup();
        String id = "https://ex.com/" + UUID.randomUUID() + "/money.json";
        createArtifact(g, "money", ArtifactType.JSON, "{\"$id\":\"" + id + "\",\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        createAuto(g, "invoice", ArtifactType.JSON,
                "{\"type\":\"object\",\"properties\":{\"m\":{\"$ref\":\"" + id + "\"}}}",
                ContentTypes.APPLICATION_JSON);

        assertRefs(g, "invoice", "1", List.of(ref(id, g, "money", "1")));
    }

    @Test
    public void explicitReferenceWins() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(g, "com.acme.Address", avroRecord("com.acme", "Address").replace("[]",
                "[{\"name\":\"street\",\"type\":\"string\",\"default\":\"\"}]"), ContentTypes.APPLICATION_JSON);

        ArtifactReference explicit = ref("com.acme.Address", g, "com.acme.Address", "1");
        createAuto(g, "Order", ArtifactType.AVRO, order("com.acme", "com.acme.Address"),
                ContentTypes.APPLICATION_JSON, List.of(explicit), null);

        assertRefs(g, "Order", "1", List.of(ref("com.acme.Address", g, "com.acme.Address", "1")));
    }

    @Test
    public void unmatchedReferencesFailTogether() throws Exception {
        String g = newGroup();
        String parent = """
                {"type":"record","namespace":"com.acme","name":"Order","fields":[
                  {"name":"a","type":"com.acme.A"},{"name":"b","type":"com.acme.B"}]}""";

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createAuto(g, "Order", ArtifactType.AVRO, parent));

        Assertions.assertEquals("ReferenceAutoMatchException", error.getName());
        Assertions.assertEquals(ReferenceAutoMatchException.TITLE, error.getTitle());
        Assertions.assertEquals(400, error.getStatus());
        String description = "No matching artifact found in group '" + g + "'.";
        Assertions.assertEquals(List.of("com.acme.A", "com.acme.B"),
                error.getCauses().stream().map(c -> c.getContext()).toList());
        Assertions.assertEquals(List.of(description, description),
                error.getCauses().stream().map(c -> c.getDescription()).toList());
        assertArtifactMissing(g, "Order");
    }

    @Test
    public void ambiguousMatchFails() throws Exception {
        String g = newGroup();
        createArtifact(g, "a1", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);
        createArtifact(g, "a2", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);

        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> createAuto(g, "Order", ArtifactType.AVRO, order("com.acme", "com.acme.Address")));

        Assertions.assertEquals(1, error.getCauses().size());
        Assertions.assertEquals("com.acme.Address", error.getCauses().get(0).getContext());
        Assertions.assertEquals("Ambiguous: matches " + g + "/a1, " + g + "/a2.",
                error.getCauses().get(0).getDescription());
        assertArtifactMissing(g, "Order");
    }

    @Test
    public void dryRunStoresNothing() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);

        CreateArtifactResponse response = createAuto(g, "Order", ArtifactType.AVRO,
                order("com.acme", "com.acme.Address"), ContentTypes.APPLICATION_JSON, null, true);

        Assertions.assertEquals("Order", response.getArtifact().getArtifactId());
        assertArtifactMissing(g, "Order");
    }

    @Test
    public void findOrCreateReturnsExistingVersion() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);
        String parent = order("com.acme", "com.acme.Address");
        createAuto(g, "Order", ArtifactType.AVRO, parent);

        CreateArtifact second = createRequest("Order", ArtifactType.AVRO, parent, ContentTypes.APPLICATION_JSON,
                true);
        CreateArtifactResponse response = clientV3.groups().byGroupId(g).artifacts().post(second,
                config -> config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION);

        Assertions.assertEquals("1", response.getVersion().getVersion());
        Assertions.assertEquals(1, clientV3.groups().byGroupId(g).artifacts().byArtifactId("Order").versions()
                .get().getCount());
    }

    @Test
    public void createVersionEndpoint() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);
        createArtifact(g, "Order", ArtifactType.AVRO, avroRecord("com.acme", "Order"),
                ContentTypes.APPLICATION_JSON);

        CreateVersion version = new CreateVersion();
        version.setAutoReferences(true);
        VersionContent content = new VersionContent();
        content.setContent(order("com.acme", "com.acme.Address"));
        content.setContentType(ContentTypes.APPLICATION_JSON);
        version.setContent(content);
        VersionMetaData vmd = clientV3.groups().byGroupId(g).artifacts().byArtifactId("Order").versions()
                .post(version);

        Assertions.assertEquals("2", vmd.getVersion());
        assertRefs(g, "Order", "2", List.of(ref("com.acme.Address", g, "com.acme.Address", "1")));
    }

    @Test
    public void explicitReferenceWinsUnderNoDuplicatesRule() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);
        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.INTEGRITY);
        rule.setConfig("NO_DUPLICATES");
        clientV3.groups().byGroupId(g).rules().post(rule);

        ArtifactReference explicit = ref("com.acme.Address", g, "com.acme.Address", "1");
        createAuto(g, "Order", ArtifactType.AVRO, order("com.acme", "com.acme.Address"),
                ContentTypes.APPLICATION_JSON, List.of(explicit), null);

        assertRefs(g, "Order", "1", List.of(ref("com.acme.Address", g, "com.acme.Address", "1")));
    }

    @Test
    public void disabledByDefault() throws Exception {
        String g = newGroup();
        createArtifact(g, "com.acme.Address", ArtifactType.AVRO, ADDRESS, ContentTypes.APPLICATION_JSON);

        CreateArtifact request = createRequest("Order", ArtifactType.AVRO, order("com.acme", "com.acme.Address"),
                ContentTypes.APPLICATION_JSON, null);
        clientV3.groups().byGroupId(g).artifacts().post(request);

        assertRefs(g, "Order", "1", List.of());
    }

    private static String newGroup() {
        return "auto-refs-" + UUID.randomUUID();
    }

    private static String avroRecord(String namespace, String name) {
        return "{\"type\":\"record\",\"namespace\":\"" + namespace + "\",\"name\":\"" + name
                + "\",\"fields\":[]}";
    }

    private static String order(String namespace, String fieldType) {
        return "{\"type\":\"record\",\"namespace\":\"" + namespace + "\",\"name\":\"Order\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"" + fieldType + "\"}]}";
    }

    private static ArtifactReference ref(String name, String groupId, String artifactId, String version) {
        ArtifactReference ref = new ArtifactReference();
        ref.setName(name);
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        return ref;
    }

    private static CreateArtifact createRequest(String artifactId, String artifactType, String content,
            String contentType, Boolean autoReferences) {
        CreateArtifact request = new CreateArtifact();
        request.setArtifactId(artifactId);
        request.setArtifactType(artifactType);
        CreateVersion version = new CreateVersion();
        version.setAutoReferences(autoReferences);
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);
        version.setContent(versionContent);
        request.setFirstVersion(version);
        return request;
    }

    private CreateArtifactResponse createAuto(String groupId, String artifactId, String artifactType,
            String content) {
        return createAuto(groupId, artifactId, artifactType, content, ContentTypes.APPLICATION_JSON, null, null);
    }

    private CreateArtifactResponse createAuto(String groupId, String artifactId, String artifactType,
            String content, String contentType) {
        return createAuto(groupId, artifactId, artifactType, content, contentType, null, null);
    }

    private CreateArtifactResponse createAuto(String groupId, String artifactId, String artifactType,
            String content, String contentType, List<ArtifactReference> references, Boolean dryRun) {
        CreateArtifact request = createRequest(artifactId, artifactType, content, contentType, true);
        request.getFirstVersion().getContent().setReferences(references);
        return clientV3.groups().byGroupId(groupId).artifacts().post(request,
                config -> config.queryParameters.dryRun = dryRun);
    }

    private void assertRefs(String groupId, String artifactId, String version, List<ArtifactReference> expected) {
        List<ArtifactReference> actual = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).references().get();
        Assertions.assertEquals(expected.stream().map(AutoReferencesTest::describe).toList(),
                actual.stream().map(AutoReferencesTest::describe).toList());
    }

    private static String describe(ArtifactReference ref) {
        return ref.getName() + " -> " + ref.getGroupId() + "/" + ref.getArtifactId() + "@" + ref.getVersion();
    }

    private void assertArtifactMissing(String groupId, String artifactId) {
        ProblemDetails error = Assertions.assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
        Assertions.assertEquals(404, error.getStatus());
    }
}
