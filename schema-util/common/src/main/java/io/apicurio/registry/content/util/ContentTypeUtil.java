package io.apicurio.registry.content.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

public final class ContentTypeUtil {
    
    public static final String CT_APPLICATION_JSON = "application/json";
    public static final String CT_APPLICATION_CREATE_EXTENDED = "application/create.extended+json";
    public static final String CT_APPLICATION_GET_EXTENDED = "application/get.extended+json";
    public static final String CT_APPLICATION_YAML = "application/x-yaml";
    public static final String CT_APPLICATION_XML = "application/xml";

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Returns true if the Content-Type of the inbound request is "application/json".
     *
     * @param ct content type
     */
    public static boolean isApplicationJson(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_JSON);
    }

    /**
     * Returns true if the Content-Type of the inbound request is "application/x-yaml".
     *
     * @param ct content type
     */
    public static boolean isApplicationYaml(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_YAML);
    }

    /**
     * Returns true if the Content-Type of the inbound request is "application/create.extended+json".
     *
     * @param ct content type
     */
    public static boolean isApplicationCreateExtended(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_CREATE_EXTENDED);
    }

    /**
     * Returns true if the Content-Type of the inbound request is "application/get.extended+json".
     *
     * @param ct content type
     */
    public static boolean isApplicationGetExtended(String ct) {
        if (ct == null) {
            return false;
        }
        return ct.contains(CT_APPLICATION_GET_EXTENDED);
    }

    /**
     * Returns true if the content can be parsed as yaml.
     */
    public static boolean isParsableYaml(ContentHandle yaml) {
        try {
            String content = yaml.content().trim();
            // it's Json or Xml
            if (content.startsWith("{") || content.startsWith("<")) {
                return false;
            }
            JsonNode root = yamlMapper.readTree(yaml.stream());
            return root != null && root.elements().hasNext();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns true if the content can be parsed as yaml.
     */
    public static boolean isParsableJson(ContentHandle content) {
        try {
            JsonNode root = jsonMapper.readTree(content.stream());
            return root != null && (root.isObject() || root.isArray());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns true if the content can be parsed as xml.
     */
    public static boolean isParsableXml(ContentHandle content) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(new InputSource(new StringReader(content.content())), new DefaultHandler());
            // If no exception is thrown, the XML is valid
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ContentHandle yamlToJson(ContentHandle yaml) {
        try {
            JsonNode root = yamlMapper.readTree(yaml.stream());
            return ContentHandle.create(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        } catch (Throwable t) {
            return yaml;
        }
    }

    public static JsonNode parseJson(ContentHandle content) throws IOException {
        JsonNode root = jsonMapper.readTree(content.stream());
        return root;
    }

    public static JsonNode parseYaml(ContentHandle content) throws IOException {
        JsonNode root = yamlMapper.readTree(content.stream());
        return root;
    }

    public static JsonNode parseJsonOrYaml(TypedContent content) throws IOException {
        JsonNode node = null;
        String contentType = content.getContentType();
        if (contentType.toLowerCase().contains("yaml") || contentType.toLowerCase().contains("yml")) {
            node = ContentTypeUtil.parseYaml(content.getContent());
        } else {
            node = ContentTypeUtil.parseJson(content.getContent());
        }

        if (!node.isObject()) {
            throw new IOException("Input is not a valid document.");
        }

        return node;
    }

    // FIXME this doesn't work for GraphQL
    public static String determineContentType(ContentHandle content) {
        if (isParsableJson(content)) {
            return CT_APPLICATION_JSON;
        }
        if (isParsableYaml(content)) {
            return CT_APPLICATION_YAML;
        }
        if (isParsableXml(content)) {
            return CT_APPLICATION_XML;
        }
        return ContentTypes.APPLICATION_PROTOBUF;
    }
}
