package io.apicurio.registry.contracts.tags;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class TagExtractorFactoryTest {

    @Test
    public void testNullAndEmptyArtifactType() {
        TagExtractorFactory factory = new TagExtractorFactory();
        Instance<TagExtractor> instance = mock(Instance.class);
        factory.extractors = instance;

        assertTrue(factory.getExtractor(null).isEmpty());
        assertTrue(factory.getExtractor("   ").isEmpty());

        verifyNoInteractions(instance);
    }

    @Test
    public void testMatchesArtifactType() {
        TagExtractorFactory factory = new TagExtractorFactory();
        Instance<TagExtractor> instance = mock(Instance.class);
        TagExtractor extractor = mock(TagExtractor.class);

        when(extractor.getArtifactType()).thenReturn("AVRO");
        when(instance.stream()).thenReturn(Stream.of(extractor));

        factory.extractors = instance;

        Optional<TagExtractor> match = factory.getExtractor("AVRO");
        assertTrue(match.isPresent());
        assertSame(extractor, match.get());
    }

    @Test
    public void testCaseSensitiveMismatch() {
        TagExtractorFactory factory = new TagExtractorFactory();
        Instance<TagExtractor> instance = mock(Instance.class);
        TagExtractor extractor = mock(TagExtractor.class);

        when(extractor.getArtifactType()).thenReturn("AVRO");
        when(instance.stream()).thenReturn(Stream.of(extractor));

        factory.extractors = instance;

        assertTrue(factory.getExtractor("avro").isEmpty());
    }
}
