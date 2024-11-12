import { createTestClient } from "./test.utils.ts";
import { SystemInfo } from "../lib/generated-client/models";
import { expect, test } from "vitest";

test("System Info", async () => {
    const client = createTestClient();
    const info: SystemInfo | undefined = await client.system.info.get();
    expect(info).toBeDefined();
    expect(info?.name).toBe("Apicurio Registry (In Memory)");
});
