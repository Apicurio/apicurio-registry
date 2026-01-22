package io.apicurio.registry.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service that fetches Apicurio Registry documentation from the web at startup
 * and ingests it into the RAG embedding store.
 */
@ApplicationScoped
public class DocumentIngestionService {

    private static final String DOCS_BASE_URL = "https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/";

    private static final List<String> DOC_PAGES = List.of(
        "assembly-intro-to-the-registry.html",
        "assembly-intro-to-registry-rules.html",
        "assembly-installing-registry-docker.html",
        "assembly-configuring-the-registry.html",
        "assembly-configuring-registry-security.html",
        "assembly-managing-registry-artifacts-ui.html",
        "assembly-managing-registry-artifacts-api.html",
        "assembly-using-the-registry-client.html",
        "assembly-using-kafka-client-serdes.html",
        "assembly-artifact-reference.html",
        "assembly-rule-reference.html",
        "assembly-config-reference.html"
    );

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    private EmbeddingStoreIngestor ingestor;

    @PostConstruct
    void init() {
        // Create ingestor with document splitter for chunking
        ingestor = EmbeddingStoreIngestor.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .documentSplitter(DocumentSplitters.recursive(500, 50))
            .build();
    }

    private volatile boolean ingestionComplete = false;
    private volatile int documentsIngested = 0;

    void onStartup(@Observes StartupEvent event) {
        Log.info("Starting Apicurio Registry documentation ingestion from web...");

        // Run ingestion asynchronously to not block startup
        CompletableFuture.runAsync(this::ingestDocumentation)
            .exceptionally(e -> {
                Log.error("Failed to ingest documentation", e);
                return null;
            });
    }

    private void ingestDocumentation() {
        List<Document> documents = new ArrayList<>();

        for (String page : DOC_PAGES) {
            try {
                String url = DOCS_BASE_URL + page;
                Log.infof("Fetching documentation: %s", url);

                org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                    .userAgent("ApicurioRegistryDemo/1.0")
                    .timeout(30000)
                    .get();

                // Extract main content
                String title = htmlDoc.title();
                Element content = htmlDoc.selectFirst("article, .content, main, #content");

                if (content == null) {
                    content = htmlDoc.body();
                }

                // Clean up the content
                String textContent = extractCleanText(content);

                if (textContent.length() > 100) {
                    Document doc = Document.from(textContent, Metadata.from("source", url)
                        .put("title", title)
                        .put("page", page));
                    documents.add(doc);
                    Log.infof("Extracted %d chars from: %s", textContent.length(), title);
                }

            } catch (IOException e) {
                Log.warnf("Failed to fetch %s: %s", page, e.getMessage());
            }
        }

        if (!documents.isEmpty()) {
            Log.infof("Ingesting %d documents into embedding store...", documents.size());
            ingestor.ingest(documents);
            documentsIngested = documents.size();
            Log.infof("Documentation ingestion complete: %d documents", documentsIngested);
        } else {
            Log.warn("No documents were fetched for ingestion");
        }

        ingestionComplete = true;
    }

    private String extractCleanText(Element content) {
        // Remove scripts, styles, navigation, etc.
        content.select("script, style, nav, header, footer, .toc, .breadcrumbs").remove();

        // Get text with some structure preserved
        StringBuilder sb = new StringBuilder();

        // Process headings and paragraphs
        Elements elements = content.select("h1, h2, h3, h4, p, li, pre, code, td");
        for (Element el : elements) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                if (el.tagName().startsWith("h")) {
                    sb.append("\n\n## ").append(text).append("\n");
                } else if (el.tagName().equals("li")) {
                    sb.append("- ").append(text).append("\n");
                } else if (el.tagName().equals("pre") || el.tagName().equals("code")) {
                    sb.append("\n```\n").append(text).append("\n```\n");
                } else {
                    sb.append(text).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    public boolean isIngestionComplete() {
        return ingestionComplete;
    }

    public int getDocumentsIngested() {
        return documentsIngested;
    }

    public IngestionStatus getStatus() {
        return new IngestionStatus(ingestionComplete, documentsIngested, DOC_PAGES.size());
    }

    public record IngestionStatus(boolean complete, int ingested, int total) {}
}
