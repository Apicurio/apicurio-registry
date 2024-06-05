package io.apicurio.registry.types.provider;

import com.google.protobuf.DescriptorProtos;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.ProtobufContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.ProtobufDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.refs.ProtobufReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.ProtobufCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ProtobufContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.Base64;
import java.util.Map;

public class ProtobufArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {
    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType.toLowerCase().contains("proto")) {
                ProtobufFile.toProtoFileElement(content.getContent().content());
                return true;
            }
        } catch (Exception e) {
            try {
                // Attempt to parse binary FileDescriptorProto
                byte[] bytes = Base64.getDecoder().decode(content.getContent().content());
                FileDescriptorUtils.fileDescriptorToProtoFile(DescriptorProtos.FileDescriptorProto.parseFrom(bytes));
                return true;
            } catch (Exception pe) {
                // Doesn't seem to be protobuf
            }
        }
        return false;
    }

    @Override
    public String getArtifactType() {
        return ArtifactType.PROTOBUF;
    }

    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return new ProtobufCompatibilityChecker();
    }

    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new ProtobufContentCanonicalizer();
    }

    @Override
    protected ContentValidator createContentValidator() {
        return new ProtobufContentValidator();
    }

    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return new ProtobufDereferencer();
    }
    
    @Override
    public ReferenceFinder getReferenceFinder() {
        return new ProtobufReferenceFinder();
    }
}
