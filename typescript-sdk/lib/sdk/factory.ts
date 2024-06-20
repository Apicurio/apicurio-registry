import { AnonymousAuthenticationProvider, AuthenticationProvider, RequestAdapter } from "@microsoft/kiota-abstractions";
import { FetchRequestAdapter } from "@microsoft/kiota-http-fetchlibrary";
import { ApicurioRegistryClient, createApicurioRegistryClient } from "../generated-client/apicurioRegistryClient.ts";

export class RegistryClientFactory {

    public static createRegistryClient(baseUrl: string, authProvider?: AuthenticationProvider): ApicurioRegistryClient {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1);
        }
        if (authProvider === undefined || authProvider === null) {
            authProvider = new AnonymousAuthenticationProvider();
        }
        const requestAdapter: RequestAdapter = new FetchRequestAdapter(authProvider);
        requestAdapter.baseUrl = baseUrl;
        return createApicurioRegistryClient(requestAdapter);
    }

}
