package io.apicurio.registry.services;

import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.ws.rs.core.UriBuilder;

@ApplicationScoped
public class FederatedSearchService {
    private static final Logger log = LoggerFactory.getLogger(FederatedSearchService.class);

    @Inject
    WebClient webClient;

    @Inject
    PeerRegistryService peerRegistryService;

    private static final long PEER_TIMEOUT_MS = 3000;

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 10000)
    protected CompletableFuture<ArtifactSearchResults> searchPeer(String peerUrl, String name, String description, List<String> labels) {
        UriBuilder uriBuilder = UriBuilder.fromUri(peerUrl).path("/apis/registry/v3/search/artifacts");
        if (name != null) uriBuilder.queryParam("name", name);
        if (description != null) uriBuilder.queryParam("description", description);
        if (labels != null) {
            for (String label : labels) {
                uriBuilder.queryParam("labels", label);
            }
        }
        
        String url = uriBuilder.build().toString();
        CompletableFuture<ArtifactSearchResults> cf = new CompletableFuture<>();
        
        webClient.getAbs(url)
                .timeout(PEER_TIMEOUT_MS)
                .send()
                .onSuccess(response -> {
                    if (response.statusCode() == 200) {
                        ArtifactSearchResults res = response.bodyAsJson(ArtifactSearchResults.class);
                        cf.complete(res);
                    } else {
                        cf.completeExceptionally(new RuntimeException("Peer returned " + response.statusCode()));
                    }
                })
                .onFailure(cf::completeExceptionally);
                
        return cf;
    }

    public ArtifactSearchResults federateSearch(ArtifactSearchResults localResults, String name, String description, List<String> labels) {
        List<String> peers = peerRegistryService.getEnabledPeers();
        if (peers == null || peers.isEmpty()) {
            return localResults;
        }

        List<CompletableFuture<ArtifactSearchResults>> futures = new ArrayList<>();
        for (String peerUrl : peers) {
            CompletableFuture<ArtifactSearchResults> future = searchPeer(peerUrl, name, description, labels)
                    .exceptionally(ex -> {
                        log.warn("Failed to fetch from peer: " + peerUrl, ex);
                        ArtifactSearchResults empty = new ArtifactSearchResults();
                        empty.setCount(0);
                        empty.setArtifacts(new ArrayList<>());
                        return empty;
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<SearchedArtifact> mergedArtifacts = localResults != null && localResults.getArtifacts() != null 
                ? new ArrayList<>(localResults.getArtifacts()) 
                : new ArrayList<>();
        for (CompletableFuture<ArtifactSearchResults> f : futures) {
            try {
                ArtifactSearchResults peerRes = f.get();
                if (peerRes != null && peerRes.getArtifacts() != null) {
                    mergedArtifacts.addAll(peerRes.getArtifacts());
                }
            } catch (Exception e) {
                log.error("Error retrieving peer search results", e);
            }
        }
        
        // Deduplicate by artifactId
        Map<String, SearchedArtifact> dedupMap = mergedArtifacts.stream()
                .collect(Collectors.toMap(
                        SearchedArtifact::getArtifactId,
                        Function.identity(),
                        (existing, replacement) -> existing // keep first
                ));

        List<SearchedArtifact> deduped = new ArrayList<>(dedupMap.values());
        
        ArtifactSearchResults finalResult = new ArtifactSearchResults();
        finalResult.setArtifacts(deduped);
        finalResult.setCount(deduped.size());
        
        return finalResult;
    }
}
