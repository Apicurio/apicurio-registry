package io.apicurio.registry.noprofile.rest.v3.impexp;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rbac.AdminResourceTest;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;

import java.util.UUID;

/**
 * Used to create the export.zip file used by the import test in {@link AdminResourceTest}.
 */
public class ExportLoader {

    private static final String CONTENT = "{\r\n" +
            "    \"openapi\": \"3.0.2\",\r\n" +
            "    \"info\": {\r\n" +
            "        \"title\": \"Empty API\",\r\n" +
            "        \"version\": \"1.0.0\",\r\n" +
            "        \"description\": \"An example API design using OpenAPI.\"\r\n" +
            "    }\r\n" +
            "}";

    public static void main(String[] args) throws Exception {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl("http://localhost:8080/apis/registry/v3");
        RegistryClient client = new RegistryClient(adapter);
        for (int idx = 0; idx < 1000; idx++) {
            System.out.println("Iteration: " + idx);
            String data = CONTENT.replace("1.0.0", "1.0." + idx);
            String artifactId = UUID.randomUUID().toString();
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI, data, ContentTypes.APPLICATION_JSON);
            client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().post(createArtifact);
            client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId(artifactId).delete();
        }

        String testContent = CONTENT.replace("Empty API", "Test Artifact");

        createVersion(client, "Artifact-1", "1.0.1");
        createVersion(client, "Artifact-1", "1.0.2");
        createVersion(client, "Artifact-1", "1.0.3");
        createVersion(client, "Artifact-2", "1.0.1");
        createVersion(client, "Artifact-3", "1.0.2");

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").rules().post(rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.admin().rules().post(rule);
    }

    private static void createVersion(RegistryClient client, String artifactId, String version) {
        String testContent = CONTENT.replace("Empty API", "Test Artifact");
        String data = testContent.replace("1.0.0", version);
        CreateVersion createVersion = TestUtils.clientCreateVersion(data, ContentTypes.APPLICATION_JSON);
        createVersion.setVersion(version);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId(artifactId).versions().post(createVersion);
    }

}
