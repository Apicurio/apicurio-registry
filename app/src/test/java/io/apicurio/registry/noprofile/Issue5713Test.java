package io.apicurio.registry.noprofile;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.v2.RegistryClient;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.MutabilityEnabledProfile;
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

@QuarkusTest
@TestProfile(MutabilityEnabledProfile.class)
public class Issue5713Test extends AbstractResourceTestBase {

    private static final String PROTO_SIMPLE = """
syntax = "proto3";

package additionalTypes;

option java_package = "additionalTypes";
option java_outer_classname = "Decimals";

message Decimal {
  int64 units = 1;
  int32 fraction = 2;
  uint32 precision = 3;
  int32 scale = 4;
}
""";


    private static final String PROTO_SIMPLE_V2 = """
syntax = "proto3";

package additionalTypes;

option java_package = "additionalTypes";
option java_outer_classname = "Decimals";

message Decimal {
  int64 units = 1;
  int32 fraction = 2;
  uint32 precision = 3;
  int32 scale = 4;
  int32 mode = 5;
}
""";

    protected RegistryClient createRestClientV2(Vertx vertx) {
        String registryV2ApiUrl = registryApiBaseUrl + "/registry/v2";
        RequestAdapter anonymousAdapter = new VertXRequestAdapter(vertx);
        anonymousAdapter.setBaseUrl(registryV2ApiUrl);
        var client = new io.apicurio.registry.rest.client.v2.RegistryClient(anonymousAdapter);
        return client;
    }

    @Test
    public void testIssue5713() throws Exception {
        RegistryClient clientV2 = createRestClientV2(vertx);
        String groupId = "default";
        String artifactId = "testIssue5713-value";

        // Create a group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        clientV3.groups().post(createGroup);

        ArtifactContent body = new ArtifactContent();
        body.setContent(PROTO_SIMPLE);
        ArtifactMetaData amd = clientV2.groups().byGroupId(groupId).artifacts().post(body, config -> {
            config.headers.put("X-Registry-ArtifactId", Set.of(artifactId));
            config.headers.put("X-Registry-Version", Set.of("1"));
            config.headers.put("Content-Type", Set.of("application/vnd.create.extended+json"));
        });
        long contentId = amd.getContentId();

        Assertions.assertNotNull(amd);
        Assertions.assertEquals(groupId, amd.getGroupId());
        Assertions.assertEquals(artifactId, amd.getId());
        Assertions.assertEquals(ArtifactType.PROTOBUF, amd.getType());
        Assertions.assertEquals("1", amd.getVersion());

        Schema latestVersion = confluentClient.getLatestVersion(artifactId);
        Assertions.assertEquals(1, latestVersion.getVersion());

        Schema versionOne = confluentClient.getVersion(artifactId, 1);
        Assertions.assertEquals(1, latestVersion.getVersion());

        int confluentId = versionOne.getId();
        Assertions.assertEquals(contentId, confluentId);
        SchemaString schemaById = confluentClient.getId(confluentId);
        Assertions.assertEquals(PROTO_SIMPLE, schemaById.getSchemaString());

        // Create V2 without content-type
        body.setContent(PROTO_SIMPLE_V2);
        VersionMetaData v2_vmd = clientV2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(body, config -> {
            config.headers.put("X-Registry-Version", Set.of("2"));
        });
        long v2_contentId = v2_vmd.getContentId();

        // Get the content of v1 by id using confluent client
        SchemaString v1ss = confluentClient.getId((int) contentId);
        Assertions.assertEquals(PROTO_SIMPLE, v1ss.getSchemaString());

        // Get the content of v2 by id using confluent client
        SchemaString v2ss = confluentClient.getId((int) v2_contentId);
        Assertions.assertEquals(PROTO_SIMPLE_V2, v2ss.getSchemaString());
    }

}
