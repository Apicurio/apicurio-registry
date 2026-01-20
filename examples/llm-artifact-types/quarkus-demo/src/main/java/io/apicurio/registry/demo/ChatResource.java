package io.apicurio.registry.demo;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import io.apicurio.registry.langchain4j.ApicurioPromptRegistry;
import io.apicurio.registry.langchain4j.ApicurioPromptTemplate;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * REST resource demonstrating Apicurio Registry + LangChain4j integration.
 *
 * <p>This resource shows how to:
 * <ul>
 *   <li>Fetch versioned prompts from Apicurio Registry</li>
 *   <li>Render prompts with variables</li>
 *   <li>Send rendered prompts to an LLM via LangChain4j</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * # Summarize a document using a registry prompt
 * curl -X POST http://localhost:8081/chat/summarize \
 *   -H "Content-Type: application/json" \
 *   -d '{"document": "...", "style": "concise", "maxWords": 100}'
 *
 * # Ask a question using a registry prompt
 * curl "http://localhost:8081/chat/ask?question=What is Apicurio?"
 * </pre>
 */
@Path("/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    ApicurioPromptRegistry promptRegistry;

    @Inject
    ChatModel chatModel;

    /**
     * Health check endpoint.
     */
    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "registry", "connected"
        );
    }

    /**
     * Summarize a document using the summarization-v1 prompt from the registry.
     */
    @POST
    @Path("/summarize")
    public SummarizeResponse summarize(SummarizeRequest request) {
        // Fetch the versioned prompt template from Apicurio Registry
        ApicurioPromptTemplate template = promptRegistry.getPrompt("summarization-v1");

        // Apply variables to the template
        Prompt prompt = template.apply(Map.of(
            "document", request.document(),
            "style", request.style() != null ? request.style() : "concise",
            "max_words", request.maxWords() != null ? request.maxWords() : 200
        ));

        // Send to LLM and get response
        String response = chatModel.chat(prompt.text());

        return new SummarizeResponse(
            response,
            "summarization-v1",
            "latest"
        );
    }

    /**
     * Summarize using a specific prompt version.
     */
    @POST
    @Path("/summarize/{version}")
    public SummarizeResponse summarizeWithVersion(
            String version,
            SummarizeRequest request) {

        // Fetch specific version from registry
        ApicurioPromptTemplate template = promptRegistry.getPrompt("summarization-v1", version);

        Prompt prompt = template.apply(Map.of(
            "document", request.document(),
            "style", request.style() != null ? request.style() : "concise",
            "max_words", request.maxWords() != null ? request.maxWords() : 200
        ));

        String response = chatModel.chat(prompt.text());

        return new SummarizeResponse(response, "summarization-v1", version);
    }

    /**
     * Ask a question using the QA prompt from the registry.
     */
    @GET
    @Path("/ask")
    public AskResponse ask(@QueryParam("question") String question) {
        // Fetch the QA prompt template
        ApicurioPromptTemplate template = promptRegistry.getPrompt("qa-prompt");

        Prompt prompt = template.apply(Map.of(
            "question", question,
            "context", "Apicurio Registry is a schema registry for managing API artifacts."
        ));

        String response = chatModel.chat(prompt.text());

        return new AskResponse(question, response);
    }

    /**
     * Preview a prompt without sending to LLM (for debugging).
     */
    @POST
    @Path("/preview")
    public PreviewResponse preview(PreviewRequest request) {
        ApicurioPromptTemplate template = promptRegistry.getPrompt(
            request.artifactId(),
            request.version()
        );

        Prompt prompt = template.apply(request.variables());

        return new PreviewResponse(
            request.artifactId(),
            request.version(),
            prompt.text()
        );
    }

    /**
     * Clear the prompt cache (useful for development).
     */
    @POST
    @Path("/cache/clear")
    public Map<String, String> clearCache() {
        promptRegistry.clearCache();
        return Map.of("status", "cache cleared");
    }

    // Request/Response records

    public record SummarizeRequest(
        String document,
        String style,
        Integer maxWords
    ) {}

    public record SummarizeResponse(
        String summary,
        String promptArtifactId,
        String promptVersion
    ) {}

    public record AskResponse(
        String question,
        String answer
    ) {}

    public record PreviewRequest(
        String artifactId,
        String version,
        Map<String, Object> variables
    ) {}

    public record PreviewResponse(
        String artifactId,
        String version,
        String renderedPrompt
    ) {}
}
