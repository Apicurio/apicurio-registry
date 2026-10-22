package io.apicurio.registry.openrpc.content.refs;

import io.apicurio.registry.openapi.content.refs.AbstractDataModelsReferenceFinder;

/**
 * OpenRPC implementation of a reference finder. Parses the OpenRPC document, finds all $refs, converts them
 * to external references, and returns them.
 */
public class OpenRpcReferenceFinder extends AbstractDataModelsReferenceFinder {
}
