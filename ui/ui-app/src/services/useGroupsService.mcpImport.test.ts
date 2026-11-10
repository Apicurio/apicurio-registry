import { describe, expect, it, vi } from "vitest";
import {
    RequestAdapter,
    RequestInformation,
    SerializationWriterFactoryRegistry
} from "@microsoft/kiota-abstractions";
import { JsonSerializationWriterFactory } from "@microsoft/kiota-serialization-json";
import { createApicurioRegistryClient } from "@apicurio/apicurio-registry-sdk";
import { McpToolImportRequest, McpToolImportResult } from "@sdk/lib/generated-client/models";

const { getRegistryClientMock } = vi.hoisted(() => {
    // useConfigService.ts reads this global at module load time; see useGroupsService.test.ts.
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window = { ApicurioRegistryConfig: registryConfig };
    return { getRegistryClientMock: vi.fn() };
});

vi.mock("@apitomy/common-ui-components", () => ({
    useAuth: () => ({})
}));

vi.mock("@utils/rest.utils.ts", () => ({
    createAuthOptions: vi.fn(),
    createEndpoint: vi.fn(),
    getRegistryClient: getRegistryClientMock
}));

import { useGroupsService } from "./useGroupsService";

const RESULT: McpToolImportResult = { groupId: "servers", artifactId: "weather", status: "UNCHANGED", tools: [] };

/**
 * A real generated client over an adapter that records each request instead of sending it, so
 * the test sees exactly the request that would go over the wire.
 */
const recordingClient = (sent: RequestInformation[]) => {
    const writers = new SerializationWriterFactoryRegistry();
    writers.contentTypeAssociatedFactories.set("application/json", new JsonSerializationWriterFactory());
    const adapter = {
        baseUrl: "http://registry.example.com/apis/registry/v3",
        getSerializationWriterFactory: () => writers,
        getParseNodeFactory: () => ({}),
        getBackingStoreFactory: () => undefined,
        send: async (requestInfo: RequestInformation) => {
            sent.push(requestInfo);
            return RESULT;
        }
    } as unknown as RequestAdapter;
    return createApicurioRegistryClient(adapter);
};

describe("useGroupsService.importMcpTools", () => {
    it("sends the MCP server headers in the body only, never as HTTP headers to the registry", async () => {
        const sent: RequestInformation[] = [];
        getRegistryClientMock.mockReturnValue(recordingClient(sent));
        const data: McpToolImportRequest = {
            url: "https://mcp.example.com/mcp",
            headers: { additionalData: { Authorization: "Bearer s3cr3t" } }
        };

        await expect(useGroupsService().importMcpTools("servers", "weather", data)).resolves.toEqual(RESULT);

        expect(sent).toHaveLength(1);
        const headerNames = [...sent[0].headers.keys()].map(name => name.toLowerCase());
        expect(headerNames).toEqual(["accept", "content-type"]);
        const body = JSON.parse(new TextDecoder().decode(sent[0].content as ArrayBuffer));
        expect(body.headers).toEqual({ Authorization: "Bearer s3cr3t" });
    });
});
