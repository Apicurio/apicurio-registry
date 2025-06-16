package io.apicurio.registry.script;

import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerRequest;
import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorRequest;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorResponse;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderRequest;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderResponse;
import io.roastedroot.quickjs4j.annotations.ScriptInterface;

@ScriptInterface(context = ArtifactTypeScriptProviderContext.class)
public interface ArtifactTypeScriptProvider {

    boolean acceptsContent(ContentAccepterRequest request);

    CompatibilityCheckerResponse testCompatibility(CompatibilityCheckerRequest request);

    ContentCanonicalizerResponse canonicalize(ContentCanonicalizerRequest request);

    ContentDereferencerResponse dereference(ContentDereferencerRequest request);

    ContentDereferencerResponse rewriteReferences(ContentDereferencerRequest request);

    ContentValidatorResponse validate(ContentValidatorRequest request);

    ContentValidatorResponse validateReferences(ContentValidatorRequest request);

    ReferenceFinderResponse findExternalReferences(ReferenceFinderRequest request);

}
