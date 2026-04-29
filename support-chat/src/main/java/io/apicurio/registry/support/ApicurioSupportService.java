package io.apicurio.registry.support;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.RenderPromptRequest;
import io.apicurio.registry.rest.client.models.RenderPromptRequestVariables;
import io.apicurio.registry.rest.client.models.RenderPromptResponse;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Support service for Apicurio Registry that demonstrates LLM artifact type integration.
 *
 * <p>This service uses the Registry SDK and /render endpoint to render PROMPT_TEMPLATE artifacts
 * and provides AI-powered support for Apicurio Registry questions.
 */
@ApplicationScoped
public class ApicurioSupportService {

    private static final String SYSTEM_PROMPT_ARTIFACT = "apicurio-support-system-prompt";
    private static final String CHAT_PROMPT_ARTIFACT = "apicurio-support-chat-prompt";

    private static final String FALLBACK_SYSTEM_PROMPT = """
        You are a helpful support assistant for Apicurio Registry, an open-source API and schema registry.
        Apicurio Registry stores and manages APIs and schemas such as AVRO, PROTOBUF, JSON, OPENAPI, \
        ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML, PROMPT_TEMPLATE, and MODEL_SCHEMA.
        Answer user questions about Apicurio Registry features, configuration, deployment, and usage.
        Be concise and helpful. If you don't know the answer, say so.
        """;

    @ConfigProperty(name = "apicurio.registry.url", defaultValue = "http://localhost:8080")
    String registryUrl;

    @ConfigProperty(name = "apicurio.registry.default-group", defaultValue = "default")
    String registryGroup;

    @Inject
    SupportAiService aiService;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    private ContentRetriever contentRetriever;
    private RegistryClient registryClient;

    // Conversation memory per session
    private final Map<String, List<ConversationTurn>> conversationMemory = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(5)
            .minScore(0.6)
            .build();

        RegistryClientOptions options = RegistryClientOptions.create(registryUrl);
        registryClient = RegistryClientFactory.create(options);
    }

    public String getSystemPrompt() {
        return getSystemPrompt(null);
    }

    public String getSystemPrompt(String version) {
        try {
            Map<String, Object> variables = Map.of(
                "supported_artifact_types",
                "AVRO, PROTOBUF, JSON, OPENAPI, ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML, PROMPT_TEMPLATE, MODEL_SCHEMA",
                "additional_context", ""
            );
            return renderPrompt(SYSTEM_PROMPT_ARTIFACT, version, variables);
        } catch (Exception e) {
            Log.warnf("Failed to fetch system prompt from registry, using fallback: %s", e.getMessage());
            return FALLBACK_SYSTEM_PROMPT;
        }
    }

    public String chat(String sessionId, String question) {
        return chat(sessionId, question, null, null);
    }

    public String chat(String sessionId, String question, String systemPromptVersion, String chatPromptVersion) {
        String systemPrompt = getSystemPrompt(systemPromptVersion);
        String conversationHistory = buildConversationHistory(sessionId);
        String relevantDocs = retrieveRelevantDocumentation(question);

        String additionalContext = relevantDocs.isEmpty() ? "" :
            "\n\n## Relevant Documentation\n" + relevantDocs;

        String prompt;
        try {
            Map<String, Object> variables = Map.of(
                "system_prompt", systemPrompt + additionalContext,
                "question", question,
                "conversation_history", conversationHistory,
                "include_examples", true
            );
            prompt = renderPrompt(CHAT_PROMPT_ARTIFACT, chatPromptVersion, variables);
        } catch (Exception e) {
            Log.warnf("Failed to fetch chat prompt from registry, using fallback: %s", e.getMessage());
            prompt = systemPrompt + additionalContext
                + "\n\nConversation history:\n" + conversationHistory
                + "\n\nUser question: " + question;
        }

        String response = aiService.chat(prompt);
        addToConversationMemory(sessionId, question, response);

        return response;
    }

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
                    if (text.length() > 1000) {
                        text = text.substring(0, 1000) + "...";
                    }
                    return "From " + (source != null ? source : "documentation") + ":\n" + text;
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "";
        }
    }

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

    public String getTemplateContent(String artifactId) {
        return getTemplateContent(artifactId, null);
    }

    public String getTemplateContent(String artifactId, String version) {
        try {
            String versionExpression = version != null ? version : "branch=latest";
            InputStream content = registryClient.groups()
                .byGroupId(registryGroup)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersionExpression(versionExpression)
                .content()
                .get();
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch template content: " + artifactId, e);
        }
    }

    public void clearConversation(String sessionId) {
        conversationMemory.remove(sessionId);
    }

    public List<ConversationTurn> getConversationHistory(String sessionId) {
        return conversationMemory.getOrDefault(sessionId, List.of());
    }

    private String renderPrompt(String artifactId, String version, Map<String, Object> variables) {
        try {
            String versionExpression = version != null ? version : "branch=latest";

            RenderPromptRequest request = new RenderPromptRequest();
            RenderPromptRequestVariables vars = new RenderPromptRequestVariables();
            vars.setAdditionalData(new HashMap<>(variables));
            request.setVariables(vars);

            RenderPromptResponse response = registryClient.groups()
                .byGroupId(registryGroup)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .byVersionExpression(versionExpression)
                .render()
                .post(request);

            return response.getRendered();
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

    public record ConversationTurn(String question, String answer, long timestamp) {}
}
