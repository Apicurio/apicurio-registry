package io.apicurio.registry.support;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that fetches Apicurio Registry documentation from the web at startup
 * and ingests it into the RAG embedding store.
 */
@ApplicationScoped
public class DocumentIngestionService {

    private static final String DOCS_BASE_URL = "https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/";

    // Reduced to essential pages to minimize embedding API calls
    private static final List<String> DOC_PAGES = List.of(
        "assembly-intro-to-the-registry.html",
        "assembly-installing-registry-docker.html",
        "assembly-configuring-the-registry.html",
        "assembly-managing-registry-artifacts-api.html",
        "assembly-using-the-registry-client.html",
        "assembly-using-kafka-client-serdes.html"
    );

    // Gemini free tier: 100 embedding requests per minute
    private static final int BATCH_SIZE = 10;
    private static final long BATCH_DELAY_MS = 65000; // 65 seconds between batches

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ManagedExecutor managedExecutor;

    private DocumentSplitter splitter;

    @PostConstruct
    void init() {
        // Use larger chunks (2000 chars) to reduce total number of embedding calls
        splitter = DocumentSplitters.recursive(2000, 100);
    }

    private volatile boolean ingestionComplete = false;
    private volatile int documentsIngested = 0;

    void onStartup(@Observes StartupEvent event) {
        Log.info("Starting Apicurio Registry documentation ingestion from web...");

        // Run ingestion asynchronously using Quarkus-managed executor to preserve classloader context
        managedExecutor.runAsync(this::ingestDocumentation)
            .exceptionally(e -> {
                Log.error("Failed to ingest documentation", e);
                return null;
            });
    }

    private void ingestDocumentation() {
        // First, fetch all documents and split into segments
        List<TextSegment> allSegments = new ArrayList<>();

        for (String page : DOC_PAGES) {
            try {
                String url = DOCS_BASE_URL + page;
                Log.infof("Fetching documentation: %s", url);

                org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                    .userAgent("ApicurioRegistryDemo/1.0")
                    .timeout(30000)
                    .get();

                String title = htmlDoc.title();
                Element content = htmlDoc.selectFirst("article, .content, main, #content");

                if (content == null) {
                    content = htmlDoc.body();
                }

                String textContent = extractCleanText(content);

                if (textContent.length() > 100) {
                    Document doc = Document.from(textContent, Metadata.from("source", url)
                        .put("title", title)
                        .put("page", page));
                    List<TextSegment> segments = splitter.split(doc);
                    allSegments.addAll(segments);
                    Log.infof("Extracted %d chars (%d segments) from: %s",
                        textContent.length(), segments.size(), title);
                }

            } catch (IOException e) {
                Log.warnf("Failed to fetch %s: %s", page, e.getMessage());
            }
        }

        if (allSegments.isEmpty()) {
            Log.warn("No documents were fetched for ingestion");
            ingestionComplete = true;
            return;
        }

        Log.infof("Total segments to embed: %d (in batches of %d)", allSegments.size(), BATCH_SIZE);

        // Embed in small batches with delays to respect Gemini free tier rate limits
        for (int i = 0; i < allSegments.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allSegments.size());
            List<TextSegment> batch = allSegments.subList(i, end);

            Log.infof("Embedding batch %d-%d of %d segments...", i + 1, end, allSegments.size());

            try {
                Response<List<Embedding>> embeddings = embeddingModel.embedAll(batch);
                embeddingStore.addAll(embeddings.content(), batch);
                documentsIngested = end;
            } catch (Exception e) {
                Log.warnf("Failed to embed batch %d-%d: %s. Waiting and retrying...", i + 1, end, e.getMessage());
                // Wait longer on failure (likely rate limited)
                sleep(BATCH_DELAY_MS);
                try {
                    Response<List<Embedding>> embeddings = embeddingModel.embedAll(batch);
                    embeddingStore.addAll(embeddings.content(), batch);
                    documentsIngested = end;
                } catch (Exception retryEx) {
                    Log.errorf("Retry failed for batch %d-%d, skipping: %s", i + 1, end, retryEx.getMessage());
                    continue;
                }
            }

            // Pause between batches to stay under rate limit
            if (end < allSegments.size()) {
                Log.infof("Rate limit pause (%ds) before next batch...", BATCH_DELAY_MS / 1000);
                if (!sleep(BATCH_DELAY_MS)) {
                    break;
                }
            }
        }

        Log.infof("Documentation ingestion complete: %d segments embedded", documentsIngested);
        ingestionComplete = true;
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Documentation ingestion interrupted");
            return false;
        }
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
