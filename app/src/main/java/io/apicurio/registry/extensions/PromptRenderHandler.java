package io.apicurio.registry.extensions;

import io.apicurio.registry.rest.v3.beans.RenderPromptRequest;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;

/**
 * Extension point implementing the v3 {@code .../versions/{versionExpression}/render} endpoint.
 * <p>
 * The endpoint is part of the core REST API, but the behaviour belongs to the optional agents module.
 * When no bean implements this interface the endpoint responds with 404.
 * </p>
 */
public interface PromptRenderHandler {

    /**
     * Renders the prompt template stored in the given artifact version. Authorization has already been
     * checked by the caller; required parameters have been validated.
     *
     * @param groupId           the group id as given in the request
     * @param artifactId        the artifact id
     * @param versionExpression the version expression (version or branch)
     * @param request           the render request
     * @return the rendered prompt
     */
    RenderPromptResponse render(String groupId, String artifactId, String versionExpression,
            RenderPromptRequest request);
}
