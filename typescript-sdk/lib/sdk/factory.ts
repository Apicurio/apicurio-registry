import {
    AnonymousAuthenticationProvider,
    AuthenticationProvider,
    RequestAdapter,
    ParseNodeFactoryRegistry,
    ParseNodeFactory, type SerializationWriterFactory
} from "@microsoft/kiota-abstractions";
import {
    FetchRequestAdapter,
    KiotaClientFactory,
    Middleware,
    MiddlewareFactory
} from "@microsoft/kiota-http-fetchlibrary";
import { JsonParseNodeFactory, JsonSerializationWriterFactory } from "@microsoft/kiota-serialization-json";
import { ApicurioRegistryClient, createApicurioRegistryClient } from "../generated-client/apicurioRegistryClient.js";
import { SerializationWriterFactoryRegistry } from "@microsoft/kiota-abstractions";

// Locally defined parse node factory (for parsing responses)
const localParseNodeFactory: ParseNodeFactoryRegistry = new ParseNodeFactoryRegistry();
const jsonParseNodeFactory: ParseNodeFactory = new JsonParseNodeFactory();
localParseNodeFactory.contentTypeAssociatedFactories.set(jsonParseNodeFactory.getValidContentType(), jsonParseNodeFactory);

// Locally defined serializer factory (for serializing requests)
const localSerializationWriterFactory: SerializationWriterFactoryRegistry = new SerializationWriterFactoryRegistry();
const jsonSerializer: SerializationWriterFactory = new JsonSerializationWriterFactory();
localSerializationWriterFactory.contentTypeAssociatedFactories.set(jsonSerializer.getValidContentType(), jsonSerializer);


export class RegistryClientFactory {

    public static createRegistryClient(baseUrl: string, authProvider?: AuthenticationProvider, middlewares: Middleware[] = []): ApicurioRegistryClient {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1);
        }
        if (authProvider === undefined || authProvider === null) {
            authProvider = new AnonymousAuthenticationProvider();
        }

        let finalMiddlewares: Middleware[] | undefined = middlewares;
        if (middlewares.length === 0) {
            finalMiddlewares = undefined;
        } else {
            finalMiddlewares = [...MiddlewareFactory.getDefaultMiddlewares(), ...middlewares];
        }

        const http = KiotaClientFactory.create(undefined, finalMiddlewares);
        const requestAdapter: RequestAdapter = new FetchRequestAdapter(authProvider, localParseNodeFactory, localSerializationWriterFactory, http);
        requestAdapter.baseUrl = baseUrl;
        return createApicurioRegistryClient(requestAdapter);
    }

}
