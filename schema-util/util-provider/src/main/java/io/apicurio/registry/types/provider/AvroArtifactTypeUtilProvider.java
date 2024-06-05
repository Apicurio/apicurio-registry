package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.content.dereference.AvroDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.AvroContentExtractor;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.compatibility.AvroCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.validity.AvroContentValidator;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.types.ArtifactType;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class AvroArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    private static final Pattern QUOTED_BRACKETS = Pattern.compile(": *\"\\{}\"");

    /**
     * Given a content removes any quoted brackets. This is useful for some validation corner cases in avro where some libraries detects quoted brackets as valid and others as invalid
     */
    private static String removeQuotedBrackets(String content) {
        return QUOTED_BRACKETS.matcher(content).replaceAll(":{}");
    }

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType.toLowerCase().contains("json") && ContentTypeUtil.isParsableJson(content.getContent())) {
                // Avro without quote
                final Schema.Parser parser = new Schema.Parser();
                final List<Schema> schemaRefs = new ArrayList<>();
                for (Map.Entry<String, TypedContent> referencedContent : resolvedReferences.entrySet()) {
                    if (!parser.getTypes().containsKey(referencedContent.getKey())) {
                        Schema schemaRef = parser.parse(referencedContent.getValue().getContent().content());
                        schemaRefs.add(schemaRef);
                    }
                }
                final Schema schema = parser.parse(removeQuotedBrackets(content.getContent().content()));
                schema.toString(schemaRefs, false);
                return true;
            }
        } catch (Exception e) {
            //ignored
        }
        return false;
    }

    @Override
    public String getArtifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new AvroCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new EnhancedAvroContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new AvroContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return new AvroContentExtractor();
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return new AvroDereferencer();
    }
    
    @Override
    public ReferenceFinder getReferenceFinder() {
        return new JsonSchemaReferenceFinder();
    }
}
