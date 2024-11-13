import {
    AnonymousAuthenticationProvider,
    AuthenticationProvider,
    RequestAdapter,
    ParseNodeFactoryRegistry,
    ParseNodeFactory, type SerializationWriterFactory
} from "@microsoft/kiota-abstractions";
import { FetchRequestAdapter, HeadersInspectionHandler, KiotaClientFactory, ParametersNameDecodingHandler, RedirectHandler, RetryHandler, UserAgentHandler } from "@microsoft/kiota-http-fetchlibrary";
import { JsonParseNodeFactory, JsonSerializationWriterFactory } from "@microsoft/kiota-serialization-json";
import { ApicurioRegistryClient, createApicurioRegistryClient } from "../generated-client/apicurioRegistryClient.ts";
import {
    SerializationWriterFactoryRegistry
} from "@microsoft/kiota-abstractions/dist/es/src/serialization/serializationWriterFactoryRegistry";

// Locally defined parse node factory (for parsing responses)
const localParseNodeFactory: ParseNodeFactoryRegistry = new ParseNodeFactoryRegistry();
const jsonParseNodeFactory: ParseNodeFactory = new JsonParseNodeFactory();
localParseNodeFactory.contentTypeAssociatedFactories.set(jsonParseNodeFactory.getValidContentType(), jsonParseNodeFactory);

// Locally defined serializer factory (for serializing requests)
const localSerializationWriterFactory: SerializationWriterFactoryRegistry = new SerializationWriterFactoryRegistry();
const jsonSerializer: SerializationWriterFactory = new JsonSerializationWriterFactory();
localSerializationWriterFactory.contentTypeAssociatedFactories.set(jsonSerializer.getValidContentType(), jsonSerializer);


export class RegistryClientFactory {

    public static createRegistryClient(baseUrl: string, authProvider?: AuthenticationProvider): ApicurioRegistryClient {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1);
        }
        if (authProvider === undefined || authProvider === null) {
            authProvider = new AnonymousAuthenticationProvider();
        }
        const http = KiotaClientFactory.create(undefined, [
            new RetryHandler(), new RedirectHandler(), new ParametersNameDecodingHandler(), new UserAgentHandler(),  new HeadersInspectionHandler()
        ]);
        const requestAdapter: RequestAdapter = new FetchRequestAdapter(authProvider, localParseNodeFactory, localSerializationWriterFactory, http);
        requestAdapter.baseUrl = baseUrl;
        return createApicurioRegistryClient(requestAdapter);
    }

}
