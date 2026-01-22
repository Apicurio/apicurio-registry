package io.apicurio.registry.demo;

import io.apicurio.registry.langchain4j.ApicurioPromptRegistry;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ModelInfo;
import io.apicurio.registry.rest.client.models.ModelSearchResults;
import io.apicurio.registry.rest.client.search.modelsrequests.ModelsRequestBuilder;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST resource demonstrating Apicurio Registry LLM artifact types integration.
 *
 * <p>This resource showcases:
 * <ul>
 *   <li><b>PROMPT_TEMPLATE</b>: System and chat prompts stored in registry</li>
 *   <li><b>MODEL_SCHEMA</b>: Model search by capabilities, provider, context window</li>
 *   <li><b>Variable substitution</b>: Dynamic prompt rendering with variables</li>
 *   <li><b>Versioned prompts</b>: Fetching specific prompt versions</li>
 *   <li><b>Conversation memory</b>: Session-based chat with context</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * # Create a chat session
 * curl -X POST http://localhost:8081/support/session
 *
 * # Chat with session
 * curl -X POST http://localhost:8081/support/chat/{sessionId} \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "How do I install Apicurio Registry?"}'
 *
 * # Search for AI models
 * curl "http://localhost:8081/support/models?capability=vision&provider=openai"
 *
 * # Preview a prompt template
 * curl -X POST http://localhost:8081/support/prompts/preview \
 *   -H "Content-Type: application/json" \
 *   -d '{"artifactId": "apicurio-support-system-prompt"}'
 * </pre>
 */
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupportChatResource {

    @Inject
    ApicurioSupportService supportService;

    @Inject
    ApicurioPromptRegistry promptRegistry;

    @Inject
    RegistryClient registryClient;

    @Inject
    DocumentIngestionService documentIngestionService;

    // Track active sessions
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // =========================================================================
    // Health & Info Endpoints
    // =========================================================================

    /**
     * Health check for the support chat service.
     */
    @GET
    @Path("/health")
    public Map<String, Object> health() {
        DocumentIngestionService.IngestionStatus ragStatus = documentIngestionService.getStatus();
        return Map.of(
            "status", "UP",
            "service", "Apicurio Registry Support Chat",
            "features", List.of(
                "PROMPT_TEMPLATE integration",
                "MODEL_SCHEMA search",
                "Conversation memory",
                "Versioned prompts",
                "RAG with web documentation"
            ),
            "activeSessions", activeSessions.size(),
            "rag", Map.of(
                "status", ragStatus.complete() ? "ready" : "ingesting",
                "documentsIngested", ragStatus.ingested(),
                "totalDocuments", ragStatus.total()
            )
        );
    }

    /**
     * Get RAG ingestion status.
     */
    @GET
    @Path("/rag/status")
    public DocumentIngestionService.IngestionStatus getRagStatus() {
        return documentIngestionService.getStatus();
    }

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Create a new chat session.
     */
    @POST
    @Path("/session")
    public SessionResponse createSession() {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, new SessionInfo(sessionId, System.currentTimeMillis()));

        return new SessionResponse(
            sessionId,
            "Session created. Chat prompts will be fetched from Apicurio Registry.",
            activeSessions.size()
        );
    }

    /**
     * Get session information.
     */
    @GET
    @Path("/session/{sessionId}")
    public SessionInfo getSession(@PathParam("sessionId") String sessionId) {
        SessionInfo session = activeSessions.get(sessionId);
        if (session == null) {
            throw new jakarta.ws.rs.NotFoundException("Session not found: " + sessionId);
        }
        return session;
    }

    /**
     * End a chat session.
     */
    @DELETE
    @Path("/session/{sessionId}")
    public Map<String, String> endSession(@PathParam("sessionId") String sessionId) {
        SessionInfo removed = activeSessions.remove(sessionId);
        if (removed == null) {
            throw new jakarta.ws.rs.NotFoundException("Session not found: " + sessionId);
        }
        supportService.clearConversation(sessionId);
        return Map.of(
            "status", "Session ended",
            "sessionId", sessionId
        );
    }

    // =========================================================================
    // Chat Endpoints (PROMPT_TEMPLATE demonstration)
    // =========================================================================

    /**
     * Send a chat message using registry-stored prompts.
     *
     * <p>Demonstrates:
     * <ul>
     *   <li>Fetching PROMPT_TEMPLATE artifacts from registry</li>
     *   <li>Variable substitution in prompts</li>
     *   <li>Conversation memory across turns</li>
     * </ul>
     */
    @POST
    @Path("/chat/{sessionId}")
    public ChatResponse chat(
            @PathParam("sessionId") String sessionId,
            ChatRequest request) {

        // Validate session
        SessionInfo session = activeSessions.get(sessionId);
        if (session == null) {
            throw new jakarta.ws.rs.NotFoundException(
                "Session not found: " + sessionId + ". Create a session first with POST /support/session"
            );
        }

        // Update session
        session.updateLastActivity();
        session.incrementMessageCount();

        // Get response using registry prompts
        long startTime = System.currentTimeMillis();
        String response = supportService.chat(
            sessionId,
            request.message(),
            request.systemPromptVersion(),
            request.chatPromptVersion()
        );
        long responseTime = System.currentTimeMillis() - startTime;

        return new ChatResponse(
            sessionId,
            request.message(),
            response,
            responseTime,
            "apicurio-support-system-prompt",
            "apicurio-support-chat-prompt"
        );
    }

    /**
     * Quick chat without session management.
     */
    @POST
    @Path("/ask")
    public QuickChatResponse quickChat(ChatRequest request) {
        String tempSessionId = "quick-" + UUID.randomUUID();

        long startTime = System.currentTimeMillis();
        String response = supportService.chat(tempSessionId, request.message());
        long responseTime = System.currentTimeMillis() - startTime;

        // Clean up temporary session
        supportService.clearConversation(tempSessionId);

        return new QuickChatResponse(
            request.message(),
            response,
            responseTime
        );
    }

    // =========================================================================
    // Prompt Template Endpoints
    // =========================================================================

    /**
     * Get the raw system prompt template from registry.
     */
    @GET
    @Path("/prompts/system")
    public PromptTemplateResponse getSystemPrompt(
            @QueryParam("version") String version) {

        String template = promptRegistry.getTemplateContent("apicurio-support-system-prompt");
        String rendered = supportService.getSystemPrompt(version);

        return new PromptTemplateResponse(
            "apicurio-support-system-prompt",
            version != null ? version : "latest",
            template,
            rendered
        );
    }

    /**
     * Get a prompt template from registry.
     */
    @GET
    @Path("/prompts/{artifactId}")
    public PromptTemplateResponse getPrompt(
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version) {

        String template = version != null
            ? promptRegistry.getPrompt(artifactId, version).getTemplate()
            : promptRegistry.getPrompt(artifactId).getTemplate();

        return new PromptTemplateResponse(
            artifactId,
            version != null ? version : "latest",
            template,
            null
        );
    }

    /**
     * Preview a rendered prompt without calling the LLM.
     */
    @POST
    @Path("/prompts/preview")
    public PromptPreviewResponse previewPrompt(PromptPreviewRequest request) {
        String rendered = supportService.previewPrompt(
            request.sessionId() != null ? request.sessionId() : "preview",
            request.question()
        );

        return new PromptPreviewResponse(
            request.question(),
            rendered,
            "apicurio-support-system-prompt",
            "apicurio-support-chat-prompt"
        );
    }

    /**
     * Clear the prompt cache.
     */
    @POST
    @Path("/prompts/cache/clear")
    public Map<String, String> clearPromptCache() {
        supportService.clearPromptCache();
        return Map.of("status", "Prompt cache cleared");
    }

    // =========================================================================
    // Model Schema Endpoints (MODEL_SCHEMA demonstration)
    // =========================================================================

    /**
     * Search for AI models by capabilities.
     *
     * <p>Demonstrates searching MODEL_SCHEMA artifacts by:
     * <ul>
     *   <li>Capabilities (e.g., vision, function_calling, chat)</li>
     *   <li>Provider (e.g., openai, anthropic)</li>
     *   <li>Context window size</li>
     * </ul>
     *
     * <p>Example: GET /support/models?capability=vision&provider=openai
     */
    @GET
    @Path("/models")
    public ModelSearchResponse searchModels(
            @QueryParam("capability") List<String> capabilities,
            @QueryParam("provider") String provider,
            @QueryParam("minContextWindow") Long minContextWindow,
            @QueryParam("maxContextWindow") Long maxContextWindow,
            @QueryParam("limit") Integer limit) {

        // Call the registry's model search API
        ModelSearchResults results = registryClient.search().models().get(config -> {
            ModelsRequestBuilder.GetQueryParameters params = config.queryParameters;
            if (capabilities != null && !capabilities.isEmpty()) {
                params.capability = capabilities.toArray(new String[0]);
            }
            if (provider != null) {
                params.provider = provider;
            }
            if (minContextWindow != null) {
                params.minContextWindow = minContextWindow;
            }
            if (maxContextWindow != null) {
                params.maxContextWindow = maxContextWindow;
            }
            if (limit != null) {
                params.limit = limit;
            }
        });

        // Convert to response format
        List<ModelSummary> models = List.of();
        if (results != null && results.getModels() != null) {
            models = results.getModels().stream()
                .map(m -> new ModelSummary(
                    m.getModelId(),
                    m.getProvider(),
                    m.getCapabilities() != null ? m.getCapabilities() : List.of(),
                    m.getContextWindow()
                ))
                .toList();
        }

        return new ModelSearchResponse(
            results != null ? results.getCount() : 0,
            models
        );
    }

    /**
     * Get details of a specific model by artifact ID.
     */
    @GET
    @Path("/models/{artifactId}")
    public ModelSummary getModel(@PathParam("artifactId") String artifactId) {
        // Search for the specific model
        ModelSearchResults results = registryClient.search().models().get(config -> {
            config.queryParameters.name = artifactId;
            config.queryParameters.limit = 1;
        });

        if (results == null || results.getModels() == null || results.getModels().isEmpty()) {
            throw new jakarta.ws.rs.NotFoundException("Model not found: " + artifactId);
        }

        ModelInfo model = results.getModels().get(0);
        return new ModelSummary(
            model.getModelId(),
            model.getProvider(),
            model.getCapabilities() != null ? model.getCapabilities() : List.of(),
            model.getContextWindow()
        );
    }

    // =========================================================================
    // Conversation History
    // =========================================================================

    /**
     * Get conversation history for a session.
     */
    @GET
    @Path("/history/{sessionId}")
    public List<ApicurioSupportService.ConversationTurn> getHistory(
            @PathParam("sessionId") String sessionId) {
        return supportService.getConversationHistory(sessionId);
    }

    // =========================================================================
    // Request/Response Records
    // =========================================================================

    public record ChatRequest(
        String message,
        String systemPromptVersion,
        String chatPromptVersion
    ) {}

    public record ChatResponse(
        String sessionId,
        String question,
        String answer,
        long responseTimeMs,
        String systemPromptArtifact,
        String chatPromptArtifact
    ) {}

    public record QuickChatResponse(
        String question,
        String answer,
        long responseTimeMs
    ) {}

    public record SessionResponse(
        String sessionId,
        String message,
        int totalActiveSessions
    ) {}

    public record PromptTemplateResponse(
        String artifactId,
        String version,
        String template,
        String rendered
    ) {}

    public record PromptPreviewRequest(
        String sessionId,
        String question
    ) {}

    public record PromptPreviewResponse(
        String question,
        String renderedPrompt,
        String systemPromptArtifact,
        String chatPromptArtifact
    ) {}

    public record ModelSearchResponse(
        int count,
        List<ModelSummary> models
    ) {}

    public record ModelSummary(
        String modelId,
        String provider,
        List<String> capabilities,
        Long contextWindow
    ) {}


    // Session tracking
    public static class SessionInfo {
        private final String sessionId;
        private final long createdAt;
        private long lastActivityAt;
        private int messageCount;

        public SessionInfo(String sessionId, long createdAt) {
            this.sessionId = sessionId;
            this.createdAt = createdAt;
            this.lastActivityAt = createdAt;
            this.messageCount = 0;
        }

        public String getSessionId() { return sessionId; }
        public long getCreatedAt() { return createdAt; }
        public long getLastActivityAt() { return lastActivityAt; }
        public int getMessageCount() { return messageCount; }

        public void updateLastActivity() {
            this.lastActivityAt = System.currentTimeMillis();
        }

        public void incrementMessageCount() {
            this.messageCount++;
        }
    }
}
