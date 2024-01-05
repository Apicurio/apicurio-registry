package io.apicurio.registry.noprofile.rest.v3.impexp;

import java.util.UUID;



import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.apicurio.registry.client.auth.VertXAuthFactory;

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
            ArtifactContent content = new ArtifactContent();
            content.setContent(data);
            client.groups().byGroupId("default").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });
            client.groups().byGroupId("default").artifacts().byArtifactId(artifactId).delete();
        }

        String testContent = CONTENT.replace("Empty API", "Test Artifact");

        ArtifactContent content = new ArtifactContent();
        String data = testContent.replace("1.0.0", "1.0.1");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.1");
        });
        data = testContent.replace("1.0.0", "1.0.2");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.2");
        });
        data = testContent.replace("1.0.0", "1.0.3");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-1");
            config.headers.add("X-Registry-Version", "1.0.3");
        });

        data = testContent.replace("1.0.0", "1.0.1");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-2");
            config.headers.add("X-Registry-Version", "1.0.1");
        });

        data = testContent.replace("1.0.0", "1.0.2");
        content.setContent(data);
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").versions().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", "Artifact-3");
            config.headers.add("X-Registry-Version", "1.0.2");
        });

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        client.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").rules().post(rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.admin().rules().post(rule);
    }

}
