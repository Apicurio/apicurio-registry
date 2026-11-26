import { createTestClient, generateArtifactId } from "./test.utils.js";
import { expect, test } from "vitest";

test("MCP Tool Compatibility Not Found", async () => {
    const tool = createTestClient().wellKnown.mcpTools.byGroupId("default").byArtifactId(generateArtifactId());
    const notFound = { responseStatusCode: 404, status: 404, title: expect.any(String) };

    // A missing tool is a 404 whether or not MCP tools support is enabled
    await expect(tool.compatible.get()).rejects.toMatchObject(notFound);
    await expect(tool.compatibility.get()).rejects.toMatchObject(notFound);
});
