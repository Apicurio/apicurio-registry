import {
    ApicurioRegistryClient,
    createApicurioRegistryClient
} from "../../src-generated/registry-client/apicurioRegistryClient.ts";
import { AnonymousAuthenticationProvider, AuthenticationProvider, RequestAdapter } from "@microsoft/kiota-abstractions";
import { FetchRequestAdapter } from "@microsoft/kiota-http-fetchlibrary";

export class RegistryClientFactory {

    public static createRegistryClient(baseUrl: string): ApicurioRegistryClient {
        const authProvider: AuthenticationProvider = new AnonymousAuthenticationProvider();
        const requestAdapter: RequestAdapter = new FetchRequestAdapter(authProvider);
        requestAdapter.baseUrl = baseUrl;
        return createApicurioRegistryClient(requestAdapter);
    }

}
