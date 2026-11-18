// The registry-clients scenario with the TypeScript SDK. Prints one line per step; demo.sh compares
// them with the other clients'.
//
// Usage: node order-scenario.ts <registry v3 URL> <schemas directory>   (Node.js 22.18 or later)
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { RegistryClientFactory } from "@apicurio/apicurio-registry-sdk";

const GROUP = "clients";
const ARTIFACT = "typescript-sdk-order";

const [registryUrl, schemas] = process.argv.slice(2);
const client = RegistryClientFactory.createRegistryClient(registryUrl);

function version(schema: string) {
    return { content: { content: readFileSync(join(schemas, schema), "utf8"), contentType: "application/json" } };
}

// The HTTP status of a request that should fail, or "none" if it succeeded
async function status(request: Promise<unknown>): Promise<string> {
    try {
        await request;
        return "none";
    } catch (e) {
        return String((e as { responseStatusCode?: number }).responseStatusCode);
    }
}

// 1. Server information
const info = await client.system.info.get();
console.log(info?.version ? "info ok" : "info empty");

// 2. Create the artifact with its first version
const created = await client.groups.byGroupId(GROUP).artifacts.post({
    artifactId: ARTIFACT,
    artifactType: "AVRO",
    firstVersion: version("order-v1.avsc")
});
console.log("created", created?.version?.version);

// 3. BACKWARD compatibility rule on the artifact
const artifact = client.groups.byGroupId(GROUP).artifacts.byArtifactId(ARTIFACT);
await artifact.rules.post({ ruleType: "COMPATIBILITY", config: "BACKWARD" });
console.log("rule", (await artifact.rules.byRuleType("COMPATIBILITY").get())?.config);

// 4. A compatible version: a new field with a default
console.log("version", (await artifact.versions.post(version("order-v2.avsc")))?.version);

// 5. An incompatible version: a new field without a default, rejected by the rule
console.log("rejected", await status(artifact.versions.post(version("order-v3-incompatible.avsc"))));

// 6. The latest version and its content
const latest = artifact.versions.byVersionExpression("branch=latest");
console.log("latest", (await latest.get())?.version);
const content = JSON.parse(new TextDecoder().decode(await latest.content.get()));
console.log("fields", content.fields.map((f: { name: string }) => f.name).join(","));

// 7. Search
const found = await client.search.artifacts.get({ queryParameters: { groupId: GROUP, artifactId: ARTIFACT } });
console.log("search", found?.count);

// 8. Delete, then read it again
await artifact.delete();
console.log("deleted");
console.log("missing", await status(artifact.get()));
