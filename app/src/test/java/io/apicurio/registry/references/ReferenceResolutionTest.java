package io.apicurio.registry.references;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReferenceResolutionTest {

    private static final ArtifactTypeUtilProviderFactory FACTORY = new DefaultArtifactTypeUtilProviderImpl(true);

    /**
     * Verifies that resolved references keep the content type of the referenced content (for example
     * application/yaml) rather than its artifact type (regression test for #10428).
     */
    @Test
    void testRecursivelyResolveReferencesUsesContentType() {
        ContentWrapperDto yamlReference = ContentWrapperDto.builder()
                .content(ContentHandle.create("openapi: 3.0.3\ninfo:\n  title: Common API\n  version: 1.0.0\npaths: {}\n"))
                .contentType(ContentTypes.APPLICATION_YAML).artifactType(ArtifactType.OPENAPI)
                .references(Collections.emptyList()).build();
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder().groupId("default")
                .artifactId("common-api").version("1").name("common-api.yaml").build();

        Map<String, TypedContent> resolved = ReferenceResolution
                .recursivelyResolveReferences(List.of(reference), ref -> yamlReference, FACTORY);

        Assertions.assertEquals(1, resolved.size());
        Assertions.assertEquals(ContentTypes.APPLICATION_YAML,
                resolved.get("common-api.yaml").getContentType());
    }
}

