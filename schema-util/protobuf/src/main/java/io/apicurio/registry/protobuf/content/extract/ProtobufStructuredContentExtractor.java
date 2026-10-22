package io.apicurio.registry.protobuf.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.wire.schema.internal.parser.FieldElement;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

/**
 * Extracts structured elements from Protobuf content for search indexing. Parses the .proto file and extracts
 * message names, field names, service names, RPC method names, enum names, and the package name.
 */
public class ProtobufStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ProtobufStructuredContentExtractor.class);

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            ProtobufFile protoFile = new ProtobufFile(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractPackage(protoFile, elements);
            extractMessages(protoFile, elements);
            extractEnums(protoFile, elements);
            extractServices(protoFile, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Protobuf: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the package name.
     */
    private void extractPackage(ProtobufFile protoFile, List<StructuredElement> elements) {
        String packageName = protoFile.getPackageName();
        if (packageName != null && !packageName.isBlank()) {
            elements.add(new StructuredElement("package", packageName));
        }
    }

    /**
     * Extracts message names and their field names.
     */
    private void extractMessages(ProtobufFile protoFile, List<StructuredElement> elements) {
        Map<String, Map<String, FieldElement>> fieldMap = protoFile.getFieldMap();
        if (fieldMap != null) {
            for (Map.Entry<String, Map<String, FieldElement>> entry : fieldMap.entrySet()) {
                String messageName = entry.getKey();
                elements.add(new StructuredElement("message", messageName));

                Map<String, FieldElement> fields = entry.getValue();
                if (fields != null) {
                    for (String fieldName : fields.keySet()) {
                        elements.add(new StructuredElement("field", fieldName));
                    }
                }
            }
        }
    }

    /**
     * Extracts enum names and their constant names.
     */
    private void extractEnums(ProtobufFile protoFile, List<StructuredElement> elements) {
        Map<String, Map<String, com.squareup.wire.schema.internal.parser.EnumConstantElement>> enumFieldMap = protoFile
                .getEnumFieldMap();
        if (enumFieldMap != null) {
            for (Map.Entry<String, Map<String, com.squareup.wire.schema.internal.parser.EnumConstantElement>> entry : enumFieldMap
                    .entrySet()) {
                String enumName = entry.getKey();
                elements.add(new StructuredElement("enum", enumName));
            }
        }
    }

    /**
     * Extracts service names and their RPC method names.
     */
    private void extractServices(ProtobufFile protoFile, List<StructuredElement> elements) {
        Map<String, Set<String>> serviceRPCnames = protoFile.getServiceRPCnames();
        if (serviceRPCnames != null) {
            for (Map.Entry<String, Set<String>> entry : serviceRPCnames.entrySet()) {
                String serviceName = entry.getKey();
                elements.add(new StructuredElement("service", serviceName));

                Set<String> rpcNames = entry.getValue();
                if (rpcNames != null) {
                    for (String rpcName : rpcNames) {
                        elements.add(new StructuredElement("rpc", rpcName));
                    }
                }
            }
        }
    }
}
