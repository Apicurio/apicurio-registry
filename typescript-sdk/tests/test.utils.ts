import { ApicurioRegistryClient } from "../lib/generated-client/apicurioRegistryClient.ts";
import { RegistryClientFactory } from "../lib/sdk";
import { v4 as uuidv4 } from "uuid";

export function createTestClient(): ApicurioRegistryClient {
    const baseUrl: string = process.env.REGISTRY_API_BASE_URL || "http://localhost:8080/apis/registry/v3/";
    return RegistryClientFactory.createRegistryClient(baseUrl);
}

export function generateGroupId(): string {
    return "group-" + uuidv4();
}

export function generateArtifactId(): string {
    return "arty-" + uuidv4();
}
