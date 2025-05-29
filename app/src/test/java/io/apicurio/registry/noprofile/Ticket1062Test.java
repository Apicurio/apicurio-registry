package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.utils.IoUtil.toStream;

/**
 * Reproducer for:  https://issues.redhat.com/browse/IPT-1062
 */
@QuarkusTest
public class Ticket1062Test extends AbstractResourceTestBase {

    private static final String PERSON_SCHEMA = "        {\n" +
            "          \"$schema\": \"https://json-schema.org/draft/2019-09/schema\",\n" +
            "          \"$id\": \"person.schema.json\",\n" +
            "          \"title\": \"Person\",\n" +
            "          \"description\": \"Schema for a person\",\n" +
            "          \"type\": \"object\",\n" +
            "          \"properties\": {\n" +
            "            \"firstName\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The person's first name.\"\n" +
            "            },\n" +
            "            \"lastName\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The person's last name.\"\n" +
            "            },\n" +
            "            \"age\": {\n" +
            "              \"type\": \"integer\",\n" +
            "              \"description\": \"The person's age in years.\",\n" +
            "              \"minimum\": 0\n" +
            "            }\n" +
            "          },\n" +
            "          \"required\": [\n" +
            "            \"firstName\",\n" +
            "            \"lastName\"\n" +
            "          ]\n" +
            "        }";

    private static final String ADDRESS_SCHEMA = "        {\n" +
            "          \"$schema\": \"https://json-schema.org/draft/2019-09/schema\",\n" +
            "          \"$id\": \"address.schema.json\",\n" +
            "          \"title\": \"Address\",\n" +
            "          \"description\": \"Schema for an address\",\n" +
            "          \"type\": \"object\",\n" +
            "          \"properties\": {\n" +
            "            \"streetAddress\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The street address.\"\n" +
            "            },\n" +
            "            \"city\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The city.\"\n" +
            "            },\n" +
            "            \"state\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The state.\"\n" +
            "            },\n" +
            "            \"postalCode\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"description\": \"The postal code.\"\n" +
            "            }\n" +
            "          },\n" +
            "          \"required\": [\n" +
            "            \"streetAddress\",\n" +
            "            \"city\",\n" +
            "            \"state\",\n" +
            "            \"postalCode\"\n" +
            "          ]\n" +
            "        }";

    private static final String CONTACT_SCHEMA = "        {\n" +
            "          \"$schema\": \"https://json-schema.org/draft/2019-09/schema\",\n" +
            "          \"$id\": \"contact.schema.json\",\n" +
            "          \"title\": \"Contact\",\n" +
            "          \"description\": \"Schema for a contact\",\n" +
            "          \"type\": \"object\",\n" +
            "          \"properties\": {\n" +
            "            \"person\": {\n" +
            "              \"$ref\": \"./person.schema.json\"\n" +
            "            },\n" +
            "            \"address\": {\n" +
            "              \"$ref\": \"./address.schema.json\"\n" +
            "            },\n" +
            "            \"email\": {\n" +
            "              \"type\": \"string\",\n" +
            "              \"format\": \"email\",\n" +
            "              \"description\": \"The contact's email address.\"\n" +
            "            }\n" +
            "          },\n" +
            "          \"required\": [\n" +
            "            \"person\",\n" +
            "            \"address\",\n" +
            "            \"email\"\n" +
            "          ]\n" +
            "        }";

    @Test
    public void testDeleteArtifactWithInboundRefs() throws Exception {
        String groupId = "default";
        String addressId = TestUtils.generateArtifactId();
        String personId = TestUtils.generateArtifactId();
        String contactId = TestUtils.generateArtifactId();

        // Create the address schema
        InputStream addressData = toStream(ADDRESS_SCHEMA);
        clientV2.createArtifact(groupId, addressId, addressData);

        // Create the person schema
        InputStream personData = toStream(PERSON_SCHEMA);
        clientV2.createArtifact(groupId, personId, personData);

        // Create the contact schema (with references)
        InputStream contactData = toStream(CONTACT_SCHEMA);
        clientV2.createArtifact(groupId, contactId, contactData, List.of(
                ref("./person.schema.json", groupId, personId, "1"),
                ref("./address.schema.json", groupId, addressId, "1")
        ));

        // Try to delete the "address" artifact, version 1 - using the ccompat API - should fail
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.deleteSchemaVersion(Map.of(), addressId, "1");
        });
        VersionMetaData addressVMD = clientV2.getArtifactVersionMetaData(groupId, addressId, "1");
        Assertions.assertNotNull(addressVMD);
        Assertions.assertEquals(addressId, addressVMD.getId());

        // Try to delete the "address" artifact itself - using the ccompat API - should fail
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.deleteSubject(Map.of(), addressId);
        });
        addressVMD = clientV2.getArtifactVersionMetaData(groupId, addressId, "1");
        Assertions.assertNotNull(addressVMD);
        Assertions.assertEquals(addressId, addressVMD.getId());
    }

    private ArtifactReference ref(String name, String groupId, String artifactId, String version) {
        ArtifactReference ref = new ArtifactReference();
        ref.setName(name);
        ref.setGroupId(groupId);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        return ref;
    }

}
