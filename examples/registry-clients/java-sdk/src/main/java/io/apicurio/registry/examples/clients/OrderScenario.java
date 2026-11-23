package io.apicurio.registry.examples.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.vertx.core.Vertx;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The registry-clients scenario with the Java SDK. Prints one line per step; demo.sh compares them
 * with the other clients'.
 * <p>
 * Usage: OrderScenario &lt;registry v3 URL&gt; &lt;schemas directory&gt;
 */
public class OrderScenario {

    private static final String GROUP = "clients";
    private static final String ARTIFACT = "java-sdk-order";

    public static void main(String[] args) throws Exception {
        String registryUrl = args[0];
        Path schemas = Path.of(args[1]);
        // The application owns the Vert.x instance the SDK sends its requests with
        Vertx vertx = Vertx.vertx();
        try {
            run(RegistryClientFactory.create(RegistryClientOptions.create(registryUrl, vertx)), schemas);
        } finally {
            vertx.close();
        }
    }

    private static void run(RegistryClient client, Path schemas) throws Exception {
        // 1. Server information
        SystemInfo info = client.system().info().get();
        System.out.println(info.getVersion() != null && !info.getVersion().isBlank() ? "info ok" : "info empty");

        // 2. Create the artifact with its first version
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(ARTIFACT);
        createArtifact.setArtifactType("AVRO");
        createArtifact.setFirstVersion(version(schemas.resolve("order-v1.avsc")));
        CreateArtifactResponse created = client.groups().byGroupId(GROUP).artifacts().post(createArtifact);
        System.out.println("created " + created.getVersion().getVersion());

        // 3. BACKWARD compatibility rule on the artifact
        CreateRule rule = new CreateRule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        var artifact = client.groups().byGroupId(GROUP).artifacts().byArtifactId(ARTIFACT);
        artifact.rules().post(rule);
        System.out.println("rule " + artifact.rules().byRuleType(RuleType.COMPATIBILITY.getValue()).get().getConfig());

        // 4. A compatible version: a new field with a default
        VersionMetaData v2 = artifact.versions().post(version(schemas.resolve("order-v2.avsc")));
        System.out.println("version " + v2.getVersion());

        // 5. An incompatible version: a new field without a default, rejected by the rule
        try {
            artifact.versions().post(version(schemas.resolve("order-v3-incompatible.avsc")));
            System.out.println("rejected none");
        } catch (ApiException e) {
            System.out.println("rejected " + e.getResponseStatusCode());
        }

        // 6. The latest version and its content
        VersionMetaData latest = artifact.versions().byVersionExpression("branch=latest").get();
        System.out.println("latest " + latest.getVersion());
        try (InputStream content = artifact.versions().byVersionExpression("branch=latest").content().get()) {
            System.out.println("fields " + fields(content));
        }

        // 7. Search
        ArtifactSearchResults found = client.search().artifacts().get(r -> {
            r.queryParameters.groupId = GROUP;
            r.queryParameters.artifactId = ARTIFACT;
        });
        System.out.println("search " + found.getCount());

        // 8. Delete, then read it again
        artifact.delete();
        System.out.println("deleted");
        try {
            artifact.get();
            System.out.println("missing none");
        } catch (ApiException e) {
            System.out.println("missing " + e.getResponseStatusCode());
        }
    }

    private static CreateVersion version(Path schema) throws Exception {
        VersionContent content = new VersionContent();
        content.setContent(Files.readString(schema));
        content.setContentType("application/json");
        CreateVersion version = new CreateVersion();
        version.setContent(content);
        return version;
    }

    private static String fields(InputStream avroSchema) throws Exception {
        JsonNode schema = new ObjectMapper().readTree(avroSchema);
        List<String> names = new ArrayList<>();
        schema.get("fields").forEach(f -> names.add(f.get("name").asText()));
        return String.join(",", names);
    }
}
