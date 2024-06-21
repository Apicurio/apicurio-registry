import {
    AnonymousAuthenticationProvider,
    AuthenticationProvider,
    RequestAdapter,
    ParseNodeFactoryRegistry,
    ParseNodeFactory
} from "@microsoft/kiota-abstractions";
import { FetchRequestAdapter } from "@microsoft/kiota-http-fetchlibrary";
import { JsonParseNodeFactory } from "@microsoft/kiota-serialization-json";
import { ApicurioRegistryClient, createApicurioRegistryClient } from "../generated-client/apicurioRegistryClient.ts";

const localParseNodeFactory: ParseNodeFactoryRegistry = new ParseNodeFactoryRegistry();
const jsonParseNodeFactory: ParseNodeFactory = new JsonParseNodeFactory();
localParseNodeFactory.contentTypeAssociatedFactories.set(jsonParseNodeFactory.getValidContentType(), jsonParseNodeFactory);

export class RegistryClientFactory {

    public static createRegistryClient(baseUrl: string, authProvider?: AuthenticationProvider): ApicurioRegistryClient {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1);
        }
        if (authProvider === undefined || authProvider === null) {
            authProvider = new AnonymousAuthenticationProvider();
        }
        const requestAdapter: RequestAdapter = new FetchRequestAdapter(authProvider, localParseNodeFactory);
        requestAdapter.baseUrl = baseUrl;
        return createApicurioRegistryClient(requestAdapter);
    }

}
