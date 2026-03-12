package io.apicurio.registry.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Support service for Apicurio Registry that demonstrates LLM artifact type integration.
 *
 * <p>This service uses the Registry's /render endpoint to render PROMPT_TEMPLATE artifacts
 * and provides AI-powered support for Apicurio Registry questions.
 *
 * <p>Key features demonstrated:
 * <ul>
 *   <li>Using the Registry /render endpoint for prompt templates</li>
 *   <li>Variable substitution in prompts</li>
 *   <li>Versioned prompt management</li>
 *   <li>Conversation memory across sessions</li>
 * </ul>
 */
@ApplicationScoped
public class ApicurioSupportService {

    private static final String SYSTEM_PROMPT_ARTIFACT = "apicurio-support-system-prompt";
    private static final String CHAT_PROMPT_ARTIFACT = "apicurio-support-chat-prompt";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "apicurio.registry.url", defaultValue = "http://localhost:8080")
    String registryUrl;

    @ConfigProperty(name = "apicurio.registry.default-group", defaultValue = "default")
    String registryGroup;

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    private ContentRetriever contentRetriever;
    private WebClient webClient;

    // Conversation memory per session
    private final Map<String, List<ConversationTurn>> conversationMemory = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // Initialize the content retriever for RAG
        contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(5)
            .minScore(0.6)
            .build();

        // Initialize Web Client for REST calls
        URI uri = URI.create(registryUrl);
        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(uri.getHost())
            .setDefaultPort(uri.getPort() != -1 ? uri.getPort() : 8080)
            .setSsl("https".equals(uri.getScheme()));
        webClient = WebClient.create(vertx, options);
    }

    /**
     * Get the system prompt from the registry.
     *
     * @return the rendered system prompt
     */
    public String getSystemPrompt() {
        return getSystemPrompt(null);
    }

    /**
     * Get the system prompt from the registry with optional version.
     *
     * @param version the prompt version (null for latest)
     * @return the rendered system prompt
     */
    public String getSystemPrompt(String version) {
        Map<String, Object> variables = Map.of(
            "supported_artifact_types",
            "AVRO, PROTOBUF, JSON, OPENAPI, ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML, PROMPT_TEMPLATE, MODEL_SCHEMA",
            "additional_context", ""
        );

        return renderPrompt(SYSTEM_PROMPT_ARTIFACT, version, variables);
    }

    /**
     * Chat with the support assistant using registry-stored prompts.
     *
     * @param sessionId the session ID for conversation memory
     * @param question the user's question
     * @return the assistant's response
     */
    public String chat(String sessionId, String question) {
        return chat(sessionId, question, null, null);
    }

    /**
     * Chat with the support assistant using specific prompt versions.
     *
     * @param sessionId the session ID for conversation memory
     * @param question the user's question
     * @param systemPromptVersion version of the system prompt (null for latest)
     * @param chatPromptVersion version of the chat prompt (null for latest)
     * @return the assistant's response
     */
    public String chat(String sessionId, String question, String systemPromptVersion, String chatPromptVersion) {
        // Get the system prompt from registry
        String systemPrompt = getSystemPrompt(systemPromptVersion);

        // Build conversation history
        String conversationHistory = buildConversationHistory(sessionId);

        // Retrieve relevant documentation using RAG
        String relevantDocs = retrieveRelevantDocumentation(question);

        // Build additional context with RAG results
        String additionalContext = relevantDocs.isEmpty() ? "" :
            "\n\n## Relevant Documentation\n" + relevantDocs;

        // Render the chat prompt template
        Map<String, Object> variables = Map.of(
            "system_prompt", systemPrompt + additionalContext,
            "question", question,
            "conversation_history", conversationHistory,
            "include_examples", true
        );

        String prompt = renderPrompt(CHAT_PROMPT_ARTIFACT, chatPromptVersion, variables);

        // Send to LLM
        String response = chatModel.generate(prompt);

        // Store in conversation memory
        addToConversationMemory(sessionId, question, response);

        return response;
    }

    /**
     * Retrieve relevant documentation from the embedding store using RAG.
     *
     * @param question the user's question
     * @return relevant documentation snippets
     */
    private String retrieveRelevantDocumentation(String question) {
        try {
            List<Content> contents = contentRetriever.retrieve(Query.from(question));
            if (contents.isEmpty()) {
                return "";
            }

            return contents.stream()
                .map(content -> {
                    String source = content.textSegment().metadata().getString("source");
                    String text = content.textSegment().text();
                    // Truncate long texts
                    if (text.length() > 1000) {
                        text = text.substring(0, 1000) + "...";
                    }
                    return "From " + (source != null ? source : "documentation") + ":\n" + text;
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            // RAG is optional - if it fails, continue without it
            return "";
        }
    }

    /**
     * Get a preview of the rendered prompt without calling the LLM.
     *
     * @param sessionId the session ID
     * @param question the user's question
     * @return the fully rendered prompt
     */
    public String previewPrompt(String sessionId, String question) {
        String systemPrompt = getSystemPrompt();
        String conversationHistory = buildConversationHistory(sessionId);

        Map<String, Object> variables = Map.of(
            "system_prompt", systemPrompt,
            "question", question,
            "conversation_history", conversationHistory,
            "include_examples", true
        );

        return renderPrompt(CHAT_PROMPT_ARTIFACT, null, variables);
    }

    /**
     * Get the raw template content from the registry.
     *
     * @param artifactId the artifact ID
     * @return the raw template content
     */
    public String getTemplateContent(String artifactId) {
        return getTemplateContent(artifactId, null);
    }

    /**
     * Get the raw template content from the registry.
     *
     * @param artifactId the artifact ID
     * @param version the version (null for latest)
     * @return the raw template content
     */
    public String getTemplateContent(String artifactId, String version) {
        try {
            String versionExpression = version != null ? version : "branch=latest";
            String path = String.format("/apis/registry/v3/groups/%s/artifacts/%s/versions/%s/content",
                registryGroup, artifactId, versionExpression);

            var future = webClient.get(path)
                .send()
                .toCompletionStage()
                .toCompletableFuture();

            var response = future.get();
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch template: " + response.statusCode() + " " + response.statusMessage());
            }
            return response.bodyAsString();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch template content: " + artifactId, e);
        }
    }

    /**
     * Clear conversation memory for a session.
     *
     * @param sessionId the session ID
     */
    public void clearConversation(String sessionId) {
        conversationMemory.remove(sessionId);
    }

    /**
     * Get conversation history for a session.
     *
     * @param sessionId the session ID
     * @return list of conversation turns
     */
    public List<ConversationTurn> getConversationHistory(String sessionId) {
        return conversationMemory.getOrDefault(sessionId, List.of());
    }

    /**
     * Render a prompt template using the Registry's /render endpoint.
     *
     * @param artifactId the artifact ID
     * @param version the version (null for latest)
     * @param variables the variables to substitute
     * @return the rendered prompt text
     */
    private String renderPrompt(String artifactId, String version, Map<String, Object> variables) {
        try {
            String versionExpression = version != null ? version : "branch=latest";
            String path = String.format("/apis/registry/v3/groups/%s/artifacts/%s/versions/%s/render",
                registryGroup, artifactId, versionExpression);

            // Build request body
            Map<String, Object> requestBody = Map.of("variables", variables);
            String jsonBody = MAPPER.writeValueAsString(requestBody);

            var future = webClient.post(path)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(jsonBody))
                .toCompletionStage()
                .toCompletableFuture();

            var response = future.get();
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to render prompt: " + response.statusCode() + " " + response.statusMessage());
            }

            JsonNode responseJson = MAPPER.readTree(response.bodyAsString());
            return responseJson.get("rendered").asText();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to render prompt template: " + artifactId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render prompt template: " + artifactId, e);
        }
    }

    private String buildConversationHistory(String sessionId) {
        List<ConversationTurn> history = conversationMemory.get(sessionId);
        if (history == null || history.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ConversationTurn turn : history) {
            sb.append("User: ").append(turn.question()).append("\n");
            sb.append("Assistant: ").append(turn.answer()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private void addToConversationMemory(String sessionId, String question, String answer) {
        conversationMemory
            .computeIfAbsent(sessionId, k -> new ArrayList<>())
            .add(new ConversationTurn(question, answer, System.currentTimeMillis()));
    }

    /**
     * Record representing a conversation turn.
     */
    public record ConversationTurn(String question, String answer, long timestamp) {}
}
