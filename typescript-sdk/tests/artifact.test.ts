import { createTestClient, generateArtifactId, generateGroupId } from "./test.utils.ts";
import { ArtifactMetaData, CreateArtifact, VersionMetaData } from "../lib/generated-client/models";
import { expect, test } from "vitest";

const AVRO_CONTENT: string = `
{
    "type": "record",
    "name": "Example",
    "namespace": "com.example",
    "fields": [
        {
            "name": "fieldOne",
            "type": "string"
        }
    ]
}
`;

test("Empty Artifact", async () => {
    const client = createTestClient();
    const groupId = generateGroupId();
    const artifactId = generateArtifactId();
    const createArtifact: CreateArtifact = {
        artifactId: artifactId,
        artifactType: "AVRO",
        name: "Test Artifact",
        description: "Test Description"
    };
    const car = await client.groups.byGroupId(groupId).artifacts.post(createArtifact);
    expect(car?.artifact).toBeDefined();
    expect(car?.artifact?.artifactId).toBe(artifactId);
    expect(car?.artifact?.groupId).toBe(groupId);
    expect(car?.artifact?.name).toBe("Test Artifact");
    expect(car?.artifact?.description).toBe("Test Description");
    expect(car?.version).toBeFalsy();

    const amd: ArtifactMetaData | undefined = await client.groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).get();
    expect(amd?.artifactId).toBe(artifactId);
    expect(amd?.groupId).toBe(groupId);
    expect(amd?.name).toBe("Test Artifact");
    expect(amd?.description).toBe("Test Description");
});

test("Artifact With Version", async () => {
    const client = createTestClient();
    const groupId = generateGroupId();
    const artifactId = generateArtifactId();
    const createArtifact: CreateArtifact = {
        artifactId: artifactId,
        artifactType: "AVRO",
        name: "Test Artifact",
        description: "Test Description",
        firstVersion: {
            version: "1.0.0",
            name: "Test Version",
            description: "Test Version Description",
            isDraft: false,
            content: {
                contentType: "application/json",
                content: AVRO_CONTENT
            }
        }
    };
    const car = await client.groups.byGroupId(groupId).artifacts.post(createArtifact);
    expect(car?.artifact).toBeDefined();
    expect(car?.artifact?.artifactId).toBe(artifactId);
    expect(car?.artifact?.groupId).toBe(groupId);
    expect(car?.artifact?.name).toBe("Test Artifact");
    expect(car?.artifact?.description).toBe("Test Description");
    expect(car?.version).toBeDefined();
    expect(car?.version?.version).toBe("1.0.0");
    expect(car?.version?.name).toBe("Test Version");
    expect(car?.version?.description).toBe("Test Version Description");

    const amd: ArtifactMetaData | undefined = await client.groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).get();
    expect(amd?.groupId).toBe(groupId);
    expect(amd?.artifactId).toBe(artifactId);
    expect(amd?.name).toBe("Test Artifact");
    expect(amd?.description).toBe("Test Description");

    const vmd: VersionMetaData | undefined = await client.groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions.byVersionExpression("1.0.0").get();
    expect(vmd?.groupId).toBe(groupId);
    expect(vmd?.artifactId).toBe(artifactId);
    expect(vmd?.version).toBe("1.0.0");
    expect(vmd?.name).toBe("Test Version");
    expect(vmd?.description).toBe("Test Version Description");
});
