package io.apicurio.registry.rest.cache.etag;

import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

public final class ETagKeys {

    public static final ETagKey<Long> CONTENT_ID = new ETagKey<>("contentId");
    public static final ETagKey<Object> ENTITY_ID = new ETagKey<>("entityId");
    public static final ETagKey<UUID> UUID = new ETagKey<>("uuid");
    public static final ETagListKey<Long> REFERENCE_TREE_CONTENT_IDS = new ETagListKey<>("referenceTreeContentIds");
    public static final ETagKey<HandleReferencesType> QUERY_PARAM_REFERENCES = new ETagKey<>("references");
    public static final ETagKey<Boolean> QUERY_PARAM_RETURN_ARTIFACT_TYPE = new ETagKey<>("returnArtifactType");

    private ETagKeys() {
    }

    @SuppressWarnings("unused")
    interface Key<T> {

        String getKey();
    }

    @AllArgsConstructor(access = PRIVATE)
    public static class ETagKey<T> implements Key<T> {

        @Getter
        private final String key;
    }

    @AllArgsConstructor(access = PRIVATE)
    public static class ETagListKey<T> implements Key<T> {

        @Getter
        private final String key;
    }
}
