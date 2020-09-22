package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.storage.impl.panache.entity.Content;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ContentRepository implements PanacheRepository<Content> {

    public Content upsertContentByHash(String contentHash, byte[] contentBytes, String canonicalContentHash) {

        return findByHash(contentHash)
                .map(foundContent -> {

                    foundContent.canonicalHash = canonicalContentHash;
                    foundContent.content = contentBytes;
                    foundContent.contentHash = contentHash;
                    persist(foundContent);

                    return foundContent;
                }).orElseGet(() -> {

                    final Content content = new Content();
                    content.contentHash = contentHash;
                    content.content = contentBytes;
                    content.canonicalHash = canonicalContentHash;
                    persist(content);

                    return content;
                });
    }


    public Optional<Content> findByHash(String contentHash) {
        return find("contentHash", contentHash)
                .firstResultOptional();
    }
}
