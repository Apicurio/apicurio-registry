package io.apicurio.registry.mcp.servers;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;

import static io.quarkiverse.mcp.server.PromptMessage.withUserRole;

public class MCPPrompts {

    @Prompt(description = "Create a new artifact version in Apicurio Registry.")
    PromptMessage create_new_artifact_version() {
        /*
         * TODO: Suggestions for improvements when writing prompts:
         *
         *  - Use imperative language, not "might" or "could"
         *  - Provide examples for the LLM
         *  - Use text formatting where approapriate
         *  - Ask the LLM itself to improve the prompt
         *  - Try with other LLMs as well
         */
        return withUserRole(new TextContent(
                """
                        The assistants goal is to use the MCP server to create a new artifact version in Apicurio Registry.

                        The assistant must use these general rules during the conversation:

                        1. The assistant will ask the user if they want to change the content until the user is happy with the result.
                        2. The assistant will suggest content changes to the user, but will ask before performing the update.
                        3. The assistant will always show the content in the text panel on the right side of the window.
                        4. The assistant will always ask the user for confirmation when:
                             - Updating configuration properties,
                             - Creating group ID or artifact ID.
                                                
                        The assistant will follow these steps during the conversation:

                        1. The user might want to update an existing artifact or create a new artifact.
                                                
                           - If the user wants to update an existing artifact, the assistant will retrieve the content of the latest version, \
                             and will use it as a starting point. To assistant will ask what determine \
                             group ID and artifact ID to use, as described below.
                           - If the user wants to create a new artifact, the assistant will ask the user what kind of content do they want to create, \
                             and generate a suggestion as a starting point.

                        2. The assistant will check if the Apicurio Registry is configured to allow draft artifacts. \
                           This feature is configured with configuration property with the name "apicurio.rest.mutability.artifact-version-content.enabled". \
                           The assistant will ask the user if they want to enable the feature.
                           
                           - If this feature is enabled, the assistant will use draft artifacts.
                           - If this feature is not enabled, will ask the user if they want to create new artifact versions for each update, or wait with creating the final artifact version.
                           
                        3. The assistant might determine which artifact types are supported by Apicurio Registry, and tell the options to the user.
                                                
                        4. To create or update the content in the Registry, the assistant needs to know the group ID identifier to use.
                           - If the user wants to update an existing artifact, they will to use an existing group ID.
                           - If the user wants to create a new artifact, they might want to specify a new group ID.
                           - If the user wants to create a new artifact, they might want the assistant to generate a new group ID based on the content.
                           
                        5. Use the selected group ID if it exists, or create a new one.
                                                
                        6. To create or update the content in the Registry, the assistant needs to know the artifact ID identifier to use.
                           - If the user wants to update an existing artifact, they will want to use an existing artifact ID.
                           - If the user wants to create a new artifact, they might want to specify a new artifact ID.
                           - If the user wants to create a new artifact, they might want the assistant to generate a new artifact ID based on the content.
                                                
                        7. Use the selected artifact ID if it exists, or create a new one.
                                                
                        8. Ask the user if they want to make any changes to the generated content, and perform an update action
                            based on how the user decided to handle updates.
                            If the draft artifact feature is enabled, the assistant will publish the final artifact version by moving it to the ENABLED state.
                        """));
    }
}
