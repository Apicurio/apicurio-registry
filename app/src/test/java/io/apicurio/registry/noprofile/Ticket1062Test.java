package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Reproducer for:  https://issues.redhat.com/browse/IPT-1062
 */
@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class Ticket1062Test extends AbstractResourceTestBase {

    private static final String PERSON_SCHEMA = """
        {
          "$schema": "https://json-schema.org/draft/2019-09/schema",
          "$id": "person.schema.json",
          "title": "Person",
          "description": "Schema for a person",
          "type": "object",
          "properties": {
            "firstName": {
              "type": "string",
              "description": "The person's first name."
            },
            "lastName": {
              "type": "string",
              "description": "The person's last name."
            },
            "age": {
              "type": "integer",
              "description": "The person's age in years.",
              "minimum": 0
            }
          },
          "required": [
            "firstName",
            "lastName"
          ]
        }""";

    private static final String ADDRESS_SCHEMA = """
        {
          "$schema": "https://json-schema.org/draft/2019-09/schema",
          "$id": "address.schema.json",
          "title": "Address",
          "description": "Schema for an address",
          "type": "object",
          "properties": {
            "streetAddress": {
              "type": "string",
              "description": "The street address."
            },
            "city": {
              "type": "string",
              "description": "The city."
            },
            "state": {
              "type": "string",
              "description": "The state."
            },
            "postalCode": {
              "type": "string",
              "description": "The postal code."
            }
          },
          "required": [
            "streetAddress",
            "city",
            "state",
            "postalCode"
          ]
        }""";

    private static final String CONTACT_SCHEMA = """
        {
          "$schema": "https://json-schema.org/draft/2019-09/schema",
          "$id": "contact.schema.json",
          "title": "Contact",
          "description": "Schema for a contact",
          "type": "object",
          "properties": {
            "person": {
              "$ref": "./person.schema.json"
            },
            "address": {
              "$ref": "./address.schema.json"
            },
            "email": {
              "type": "string",
              "format": "email",
              "description": "The contact's email address."
            }
          },
          "required": [
            "person",
            "address",
            "email"
          ]
        }""";

    @Test
    public void testDeleteArtifactWithInboundRefs() throws Exception {
        String groupId = "default";
        String addressId = TestUtils.generateArtifactId();
        String personId = TestUtils.generateArtifactId();
        String contactId = TestUtils.generateArtifactId();

        // Create the address schema
        CreateArtifact createAddress = new CreateArtifact();
        createAddress.setArtifactId(addressId);
        createAddress.setArtifactType("JSON");
        createAddress.setFirstVersion(new CreateVersion());
        createAddress.getFirstVersion().setVersion("1");
        createAddress.getFirstVersion().setContent(new VersionContent());
        createAddress.getFirstVersion().getContent().setContent(ADDRESS_SCHEMA);
        createAddress.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createAddress);

        // Create the person schema
        CreateArtifact createPerson = new CreateArtifact();
        createPerson.setArtifactId(personId);
        createPerson.setArtifactType("JSON");
        createPerson.setFirstVersion(new CreateVersion());
        createPerson.getFirstVersion().setVersion("1");
        createPerson.getFirstVersion().setContent(new VersionContent());
        createPerson.getFirstVersion().getContent().setContent(PERSON_SCHEMA);
        createPerson.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createPerson);

        // Create the contact schema (with references)
        CreateArtifact createContact = new CreateArtifact();
        createContact.setArtifactId(contactId);
        createContact.setArtifactType("JSON");
        createContact.setFirstVersion(new CreateVersion());
        createContact.getFirstVersion().setVersion("1");
        createContact.getFirstVersion().setContent(new VersionContent());
        createContact.getFirstVersion().getContent().setContent(CONTACT_SCHEMA);
        createContact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createContact.getFirstVersion().getContent().setReferences(List.of(
                ref("./person.schema.json", groupId, personId, "1"),
                ref("./address.schema.json", groupId, addressId, "1")
        ));
        clientV3.groups().byGroupId(groupId).artifacts().post(createContact);

        // Try to delete the "address" artifact, version 1 - using the ccompat API - should fail
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.deleteSchemaVersion(Map.of(), addressId, "1");
        });
        VersionMetaData addressVMD = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(addressId).versions().byVersionExpression("1").get();
        Assertions.assertNotNull(addressVMD);
        Assertions.assertEquals(addressId, addressVMD.getArtifactId());

        // Try to delete the "address" artifact itself - using the ccompat API - should fail
        Assertions.assertThrows(RestClientException.class, () -> {
            confluentClient.deleteSubject(Map.of(), addressId);
        });
        addressVMD = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(addressId).versions().byVersionExpression("1").get();
        Assertions.assertNotNull(addressVMD);
        Assertions.assertEquals(addressId, addressVMD.getArtifactId());

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
