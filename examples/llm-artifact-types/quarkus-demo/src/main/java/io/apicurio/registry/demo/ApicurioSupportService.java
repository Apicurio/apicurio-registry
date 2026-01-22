package io.apicurio.registry.demo;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.apicurio.registry.langchain4j.ApicurioPromptRegistry;
import io.apicurio.registry.langchain4j.ApicurioPromptTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Support service for Apicurio Registry that demonstrates LLM artifact type integration.
 *
 * <p>This service fetches prompt templates from Apicurio Registry (PROMPT_TEMPLATE artifacts)
 * and uses them to provide AI-powered support for Apicurio Registry questions.
 *
 * <p>Key features demonstrated:
 * <ul>
 *   <li>Fetching PROMPT_TEMPLATE artifacts from registry</li>
 *   <li>Variable substitution in prompts</li>
 *   <li>Versioned prompt management</li>
 *   <li>Conversation memory across sessions</li>
 * </ul>
 */
@ApplicationScoped
public class ApicurioSupportService {

    private static final String SYSTEM_PROMPT_ARTIFACT = "apicurio-support-system-prompt";
    private static final String CHAT_PROMPT_ARTIFACT = "apicurio-support-chat-prompt";

    @Inject
    ApicurioPromptRegistry promptRegistry;

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    private ContentRetriever contentRetriever;

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
        ApicurioPromptTemplate template = promptRegistry.getPrompt(SYSTEM_PROMPT_ARTIFACT, version);
        Prompt prompt = template.apply(Map.of(
            "supported_artifact_types", "AVRO, PROTOBUF, JSON, OPENAPI, ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML, PROMPT_TEMPLATE, MODEL_SCHEMA",
            "additional_context", ""
        ));
        return prompt.text();
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

        // Get the chat prompt template from registry
        ApicurioPromptTemplate chatTemplate = promptRegistry.getPrompt(CHAT_PROMPT_ARTIFACT, chatPromptVersion);

        // Build additional context with RAG results
        String additionalContext = relevantDocs.isEmpty() ? "" :
            "\n\n## Relevant Documentation\n" + relevantDocs;

        // Render with variables
        Prompt prompt = chatTemplate.apply(Map.of(
            "system_prompt", systemPrompt + additionalContext,
            "question", question,
            "conversation_history", conversationHistory,
            "include_examples", true
        ));

        // Send to LLM
        String response = chatModel.chat(prompt.text());

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

        ApicurioPromptTemplate chatTemplate = promptRegistry.getPrompt(CHAT_PROMPT_ARTIFACT);
        Prompt prompt = chatTemplate.apply(Map.of(
            "system_prompt", systemPrompt,
            "question", question,
            "conversation_history", conversationHistory,
            "include_examples", true
        ));

        return prompt.text();
    }

    /**
     * Get the raw template content from the registry.
     *
     * @param artifactId the artifact ID
     * @return the raw template content
     */
    public String getTemplateContent(String artifactId) {
        return promptRegistry.getTemplateContent(artifactId);
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
     * Clear the prompt cache to fetch fresh templates from registry.
     */
    public void clearPromptCache() {
        promptRegistry.clearCache();
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
