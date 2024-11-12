import { createTestClient, generateGroupId } from "./test.utils.ts";
import { CreateGroup, type GroupMetaData } from "../lib/generated-client/models";
import { expect, test } from "vitest";

test("Group", async () => {
    const client = createTestClient();
    const groupId = generateGroupId();
    const createGroup: CreateGroup = {
        groupId: groupId,
        description: "Test Description"
    };
    let gmd: GroupMetaData | undefined = await client.groups.post(createGroup);
    expect(gmd).toBeDefined();
    expect(gmd?.groupId).toBe(groupId);
    expect(gmd?.description).toBe("Test Description");

    gmd = await client.groups.byGroupId(groupId).get();
    expect(gmd).toBeDefined();
    expect(gmd?.groupId).toBe(groupId);
    expect(gmd?.description).toBe("Test Description");
});
