package io.apicurio.registry.demo;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST resource for the Apicurio Registry support chat.
 *
 * <p>This resource provides a chat interface for users to ask questions about
 * Apicurio Registry. It uses RAG (Retrieval Augmented Generation) to provide
 * accurate answers based on the official documentation.
 *
 * <p>Example usage:
 * <pre>
 * # Start a new chat session
 * curl -X POST http://localhost:8081/support/session
 *
 * # Send a message
 * curl -X POST http://localhost:8081/support/chat/{sessionId} \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "How do I install Apicurio Registry using Docker?"}'
 *
 * # End the session
 * curl -X DELETE http://localhost:8081/support/session/{sessionId}
 * </pre>
 */
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupportChatResource {

    @Inject
    ApicurioSupportAssistant assistant;

    // Track active sessions (in production, use a distributed cache like Redis)
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    /**
     * Health check for the support chat service.
     */
    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "Apicurio Support Chat",
            "activeSessions", String.valueOf(activeSessions.size())
        );
    }

    /**
     * Create a new chat session.
     *
     * @return Session information including the session ID
     */
    @POST
    @Path("/session")
    public SessionResponse createSession() {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, new SessionInfo(sessionId, System.currentTimeMillis()));

        return new SessionResponse(
            sessionId,
            "Session created. You can now send messages using POST /support/chat/" + sessionId,
            activeSessions.size()
        );
    }

    /**
     * Get information about a session.
     *
     * @param sessionId The session ID
     * @return Session information
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
     * End a chat session and clear its memory.
     *
     * @param sessionId The session ID to end
     * @return Confirmation message
     */
    @DELETE
    @Path("/session/{sessionId}")
    public Map<String, String> endSession(@PathParam("sessionId") String sessionId) {
        SessionInfo removed = activeSessions.remove(sessionId);
        if (removed == null) {
            throw new jakarta.ws.rs.NotFoundException("Session not found: " + sessionId);
        }
        return Map.of(
            "status", "Session ended",
            "sessionId", sessionId
        );
    }

    /**
     * Send a chat message and get a response.
     *
     * <p>The assistant will use RAG to retrieve relevant documentation and
     * provide an accurate answer. Conversation history is maintained per session.
     *
     * @param sessionId The session ID
     * @param request The chat request containing the user's message
     * @return The assistant's response
     */
    @POST
    @Path("/chat/{sessionId}")
    public ChatResponse chat(
            @PathParam("sessionId") String sessionId,
            ChatRequest request) {

        // Validate session exists
        SessionInfo session = activeSessions.get(sessionId);
        if (session == null) {
            throw new jakarta.ws.rs.NotFoundException(
                "Session not found: " + sessionId + ". Create a session first with POST /support/session"
            );
        }

        // Update session activity
        session.updateLastActivity();
        session.incrementMessageCount();

        // Get response from AI assistant (uses RAG and conversation memory)
        long startTime = System.currentTimeMillis();
        String response = assistant.chat(sessionId, request.message());
        long responseTime = System.currentTimeMillis() - startTime;

        return new ChatResponse(
            sessionId,
            request.message(),
            response,
            responseTime
        );
    }

    /**
     * Quick chat without session management.
     *
     * <p>For simple one-off questions. No conversation history is maintained.
     *
     * @param request The chat request
     * @return The assistant's response
     */
    @POST
    @Path("/ask")
    public QuickChatResponse quickChat(ChatRequest request) {
        // Use a random session ID for stateless chat
        String tempSessionId = "quick-" + UUID.randomUUID().toString();

        long startTime = System.currentTimeMillis();
        String response = assistant.chat(tempSessionId, request.message());
        long responseTime = System.currentTimeMillis() - startTime;

        return new QuickChatResponse(
            request.message(),
            response,
            responseTime
        );
    }

    // Request/Response records

    public record ChatRequest(String message) {}

    public record ChatResponse(
        String sessionId,
        String question,
        String answer,
        long responseTimeMs
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
