import { ApicurioRegistryClient } from "../lib/generated-client/apicurioRegistryClient.js";
import { RegistryClientFactory } from "../lib/sdk/index.js";
import { v4 as uuidv4 } from "uuid";

export function createTestClient(): ApicurioRegistryClient {
    const baseUrl: string = process.env.REGISTRY_API_BASE_URL || "http://127.0.0.1:8080/apis/registry/v3/";
    console.debug("Base URL for testing: " + baseUrl);
    return RegistryClientFactory.createRegistryClient(baseUrl);
}

export function generateGroupId(): string {
    return "group-" + uuidv4();
}

export function generateArtifactId(): string {
    return "arty-" + uuidv4();
}
