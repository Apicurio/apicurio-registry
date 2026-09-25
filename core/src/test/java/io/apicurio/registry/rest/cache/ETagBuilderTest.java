package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.etag.ETagBuilder;
import io.apicurio.registry.rest.cache.etag.ETagKeys;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import jakarta.ws.rs.core.EntityTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ETagBuilderTest {

    @Test
    void singleKey() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.CONTENT_ID, 42L)
                .build();
        assertEquals("contentId=42", tag.getValue());
        assertFalse(tag.isWeak());
    }

    @Test
    void multipleKeysSortedAlphabetically() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.ENTITY_ID, 1L)
                .with(ETagKeys.CONTENT_ID, 2L)
                .build();
        // TreeMap sorts keys: contentId < entityId
        assertEquals("contentId=2;entityId=1", tag.getValue());
    }

    @Test
    void queryParamIncluded() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.ENTITY_ID, 1L)
                .with(ETagKeys.QUERY_PARAM_REFERENCES, HandleReferencesType.DEREFERENCE)
                .build();
        assertEquals("entityId=1;references=DEREFERENCE", tag.getValue());
    }

    @Test
    void listKey() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.REFERENCE_TREE_CONTENT_IDS, List.of(10L, 20L, 30L))
                .build();
        assertEquals("referenceTreeContentIds=10+20+30", tag.getValue());
    }

    @Test
    void nullValueIgnored() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.CONTENT_ID, 42L)
                .with(ETagKeys.QUERY_PARAM_REFERENCES, null)
                .build();
        assertEquals("contentId=42", tag.getValue());
    }

    @Test
    void nullListIgnored() {
        EntityTag tag = new ETagBuilder()
                .with(ETagKeys.CONTENT_ID, 42L)
                .with(ETagKeys.REFERENCE_TREE_CONTENT_IDS, null)
                .build();
        assertEquals("contentId=42", tag.getValue());
    }

    @Test
    void randomProducesUniqueValues() {
        EntityTag tag1 = new ETagBuilder().withRandom().build();
        EntityTag tag2 = new ETagBuilder().withRandom().build();
        assertNotEquals(tag1.getValue(), tag2.getValue());
    }

    @Test
    void hashedProducesDifferentFormat() {
        ETagBuilder builder = new ETagBuilder().with(ETagKeys.CONTENT_ID, 42L);
        EntityTag raw = builder.build();
        EntityTag hashed = builder.buildHashed();
        assertNotEquals(raw.getValue(), hashed.getValue());
        // Hashed value should be a SHA256 hex string (64 chars)
        assertEquals(64, hashed.getValue().length());
    }

    @Test
    void hashedIsDeterministic() {
        EntityTag h1 = new ETagBuilder().with(ETagKeys.CONTENT_ID, 42L).buildHashed();
        EntityTag h2 = new ETagBuilder().with(ETagKeys.CONTENT_ID, 42L).buildHashed();
        assertEquals(h1.getValue(), h2.getValue());
    }

    @Test
    void invalidCharactersRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new ETagBuilder().with(ETagKeys.ENTITY_ID, "value=bad").build());
        assertThrows(IllegalArgumentException.class, () ->
                new ETagBuilder().with(ETagKeys.ENTITY_ID, "value;bad").build());
        assertThrows(IllegalArgumentException.class, () ->
                new ETagBuilder().with(ETagKeys.ENTITY_ID, "value+bad").build());
        assertThrows(IllegalArgumentException.class, () ->
                new ETagBuilder().with(ETagKeys.ENTITY_ID, "value,bad").build());
    }

    @Test
    void nullKeyRejected() {
        assertThrows(NullPointerException.class, () ->
                new ETagBuilder().with(null, "value"));
    }
}
