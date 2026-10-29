package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for XSD and WSDL reference support (finder, REWRITE, DEREFERENCE, INTEGRITY and
 * FULL VALIDITY).
 */
@QuarkusTest
public class XmlReferencesTest extends AbstractResourceTestBase {

    private static final String COMMON_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:example:common"
                       xmlns:c="urn:example:common"
                       elementFormDefault="qualified">
              <xs:complexType name="Address">
                <xs:sequence>
                  <xs:element name="street" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private static final String ORDER_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:example:order"
                       xmlns:c="urn:example:common"
                       elementFormDefault="qualified">
              <xs:import namespace="urn:example:common" schemaLocation="common.xsd"/>
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="shipping" type="c:Address"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String SERVICE_WSDL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                              xmlns:tns="urn:example:service"
                              targetNamespace="urn:example:service">
              <wsdl:types>
                <xsd:schema targetNamespace="urn:example:service">
                  <xsd:import namespace="urn:example:order" schemaLocation="order.xsd"/>
                </xsd:schema>
              </wsdl:types>
            </wsdl:definitions>
            """;

    private static final String CONTENT_PATH = "/registry/v3/groups/{groupId}/artifacts/{artifactId}"
            + "/versions/branch=latest/content";

    @Test
    public void testIntegrityRequiresMapping() throws Exception {
        String groupId = newGroup("integrity");
        createArtifact(groupId, "common", ArtifactType.XSD, COMMON_XSD, ContentTypes.APPLICATION_XML);
        createGroupRule(groupId, RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name());

        RuleViolationProblemDetails violation = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifact(groupId, "order", ArtifactType.XSD, ORDER_XSD,
                        ContentTypes.APPLICATION_XML));
        assertEquals(400, violation.getStatus());
        assertEquals("RuleViolationException", violation.getName());
        assertEquals(1, violation.getCauses().size());
        assertEquals("common.xsd", violation.getCauses().get(0).getContext());
        assertEquals("Unmapped reference detected.", violation.getCauses().get(0).getDescription());

        var response = createArtifactWithReferences(groupId, "order", ArtifactType.XSD, ORDER_XSD,
                ContentTypes.APPLICATION_XML, List.of(commonRef(groupId)));
        assertEquals("order", response.getArtifact().getArtifactId());
        assertEquals("1", response.getVersion().getVersion());
    }

    @Test
    public void testValidityFullResolvesImports() throws Exception {
        String groupId = newGroup("validity");
        createGroupRule(groupId, RuleType.VALIDITY, "FULL");
        createArtifact(groupId, "common", ArtifactType.XSD, COMMON_XSD, ContentTypes.APPLICATION_XML);

        var response = createArtifactWithReferences(groupId, "order", ArtifactType.XSD, ORDER_XSD,
                ContentTypes.APPLICATION_XML, List.of(commonRef(groupId)));
        assertEquals("order", response.getArtifact().getArtifactId());

        RuleViolationProblemDetails violation = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifact(groupId, "order2", ArtifactType.XSD, ORDER_XSD,
                        ContentTypes.APPLICATION_XML));
        assertEquals(400, violation.getStatus());
        assertEquals("RuleViolationException", violation.getName());
        assertEquals("common.xsd", violation.getCauses().get(0).getContext());
        assertEquals("Unresolved reference: common.xsd", violation.getCauses().get(0).getDescription());
    }

    @Test
    public void testRewrite() throws Exception {
        String groupId = newGroup("rewrite");
        createArtifact(groupId, "common", ArtifactType.XSD, COMMON_XSD, ContentTypes.APPLICATION_XML);
        createArtifactWithReferences(groupId, "order", ArtifactType.XSD, ORDER_XSD,
                ContentTypes.APPLICATION_XML, List.of(commonRef(groupId)));

        given().when().pathParam("groupId", groupId).pathParam("artifactId", "order")
                .queryParam("references", "REWRITE").get(CONTENT_PATH).then().statusCode(200)
                .body(containsString("schemaLocation=\"http"))
                .body(containsString("/groups/" + groupId
                        + "/artifacts/common/versions/1/content?references=REWRITE\""))
                .body(not(containsString("schemaLocation=\"common.xsd\"")));
    }

    @Test
    public void testDereferenceNotSupported() throws Exception {
        String groupId = newGroup("dereference");
        createArtifact(groupId, "common", ArtifactType.XSD, COMMON_XSD, ContentTypes.APPLICATION_XML);
        createArtifactWithReferences(groupId, "order", ArtifactType.XSD, ORDER_XSD,
                ContentTypes.APPLICATION_XML, List.of(commonRef(groupId)));

        given().when().pathParam("groupId", groupId).pathParam("artifactId", "order")
                .queryParam("references", "DEREFERENCE").get(CONTENT_PATH).then().statusCode(400)
                .body("name", equalTo("DereferencingNotSupportedException"))
                .body("title", containsString("references=REWRITE"));
    }

    @Test
    public void testWsdlRewriteAndIntegrity() throws Exception {
        String groupId = newGroup("wsdl");
        createGroupRule(groupId, RuleType.INTEGRITY, IntegrityLevel.ALL_REFS_MAPPED.name());
        createArtifact(groupId, "order", ArtifactType.XSD, ORDER_XSD.replace(
                "<xs:import namespace=\"urn:example:common\" schemaLocation=\"common.xsd\"/>", "")
                .replace("type=\"c:Address\"", "type=\"xs:string\""), ContentTypes.APPLICATION_XML);

        RuleViolationProblemDetails violation = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifact(groupId, "service", ArtifactType.WSDL, SERVICE_WSDL,
                        ContentTypes.APPLICATION_XML));
        assertEquals(400, violation.getStatus());
        assertEquals("order.xsd", violation.getCauses().get(0).getContext());

        ArtifactReference orderRef = ArtifactReference.builder().name("order.xsd").groupId(groupId)
                .artifactId("order").version("1").build();
        createArtifactWithReferences(groupId, "service", ArtifactType.WSDL, SERVICE_WSDL,
                ContentTypes.APPLICATION_XML, List.of(orderRef));

        given().when().pathParam("groupId", groupId).pathParam("artifactId", "service")
                .queryParam("references", "REWRITE").get(CONTENT_PATH).then().statusCode(200)
                .body(containsString("/groups/" + groupId
                        + "/artifacts/order/versions/1/content?references=REWRITE\""))
                .body(not(containsString("schemaLocation=\"order.xsd\"")));
    }

    @Test
    public void testDetectReferences() throws Exception {
        VersionContent content = new VersionContent();
        content.setContent(ORDER_XSD);
        content.setContentType(ContentTypes.APPLICATION_XML);

        List<String> names = clientV3.content().references()
                .post(content, config -> config.queryParameters.artifactType = ArtifactType.XSD).stream()
                .map(ref -> ref.getName())
                .collect(Collectors.toList());

        assertEquals(List.of("common.xsd"), names);
    }

    private String newGroup(String name) {
        String groupId = "XmlReferencesTest-" + name + "-" + UUID.randomUUID();
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);
        return groupId;
    }

    private static ArtifactReference commonRef(String groupId) {
        return ArtifactReference.builder().name("common.xsd").groupId(groupId).artifactId("common")
                .version("1").build();
    }
}
