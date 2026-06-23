package io.apicurio.registry.thrift.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.thrift.idl.ThriftIdlParseException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.thrift.idl.ThriftIdlParser.ThriftDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThriftStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ThriftStructuredContentExtractor.class);

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            ThriftDocument document = ThriftIdlParser.parse(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            document.getNamespaces().forEach(
                    ns -> elements.add(new StructuredElement("namespace", ns.getName())));

            document.getStructs().forEach(
                    name -> elements.add(new StructuredElement("struct", name)));

            document.getEnums().forEach(
                    name -> elements.add(new StructuredElement("enum", name)));

            document.getUnions().forEach(
                    name -> elements.add(new StructuredElement("union", name)));

            document.getExceptions().forEach(
                    name -> elements.add(new StructuredElement("exception", name)));

            document.getServices().forEach(
                    name -> elements.add(new StructuredElement("service", name)));

            document.getTypedefs().forEach(
                    td -> elements.add(new StructuredElement("typedef", td.getName())));

            document.getConstants().forEach(
                    c -> elements.add(new StructuredElement("const", c.getName())));

            return elements;
        } catch (ThriftIdlParseException e) {
            log.debug("Failed to extract structured content from Thrift IDL: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
