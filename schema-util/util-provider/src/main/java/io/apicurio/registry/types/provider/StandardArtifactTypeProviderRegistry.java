package io.apicurio.registry.types.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.apicurio.registry.asyncapi.content.AsyncApiContentAccepter;
import io.apicurio.registry.asyncapi.content.canon.AsyncApiContentCanonicalizer;
import io.apicurio.registry.asyncapi.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.asyncapi.content.extract.AsyncApiContentExtractor;
import io.apicurio.registry.asyncapi.content.extract.AsyncApiStructuredContentExtractor;
import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.asyncapi.rules.validity.AsyncApiContentValidator;
import io.apicurio.registry.avro.content.AvroContentAccepter;
import io.apicurio.registry.avro.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.avro.content.dereference.AvroDereferencer;
import io.apicurio.registry.avro.content.extract.AvroContentExtractor;
import io.apicurio.registry.avro.content.extract.AvroStructuredContentExtractor;
import io.apicurio.registry.avro.content.refs.AvroReferenceFinder;
import io.apicurio.registry.avro.rules.compatibility.AvroCompatibilityChecker;
import io.apicurio.registry.avro.rules.validity.AvroContentValidator;
import io.apicurio.registry.content.AgentCardContentAccepter;
import io.apicurio.registry.content.McpToolContentAccepter;
import io.apicurio.registry.content.ModelSchemaContentAccepter;
import io.apicurio.registry.content.OdcsContractContentAccepter;
import io.apicurio.registry.content.PromptTemplateContentAccepter;
import io.apicurio.registry.content.canon.YamlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ModelSchemaDereferencer;
import io.apicurio.registry.content.dereference.PromptTemplateDereferencer;
import io.apicurio.registry.content.extract.AgentCardContentExtractor;
import io.apicurio.registry.content.extract.AgentCardStructuredContentExtractor;
import io.apicurio.registry.content.extract.McpToolContentExtractor;
import io.apicurio.registry.content.extract.McpToolStructuredContentExtractor;
import io.apicurio.registry.content.extract.ModelSchemaContentExtractor;
import io.apicurio.registry.content.extract.ModelSchemaStructuredContentExtractor;
import io.apicurio.registry.content.extract.PromptTemplateContentExtractor;
import io.apicurio.registry.content.extract.PromptTemplateStructuredContentExtractor;
import io.apicurio.registry.content.refs.AvroReferenceArtifactIdentifierExtractor;
import io.apicurio.registry.content.refs.ModelSchemaReferenceFinder;
import io.apicurio.registry.content.refs.OdcsContractReferenceFinder;
import io.apicurio.registry.content.refs.PromptTemplateReferenceFinder;
import io.apicurio.registry.graphql.content.GraphQLContentAccepter;
import io.apicurio.registry.graphql.content.canon.GraphQLContentCanonicalizer;
import io.apicurio.registry.graphql.content.extract.GraphQLStructuredContentExtractor;
import io.apicurio.registry.graphql.rules.validity.GraphQLContentValidator;
import io.apicurio.registry.iceberg.content.IcebergCompatibilityChecker;
import io.apicurio.registry.iceberg.content.IcebergContentExtractor;
import io.apicurio.registry.iceberg.content.IcebergContentValidator;
import io.apicurio.registry.iceberg.content.IcebergStructuredContentExtractor;
import io.apicurio.registry.iceberg.content.IcebergTableContentAccepter;
import io.apicurio.registry.iceberg.content.IcebergViewContentAccepter;
import io.apicurio.registry.json.content.JsonSchemaContentAccepter;
import io.apicurio.registry.json.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.json.content.dereference.JsonSchemaDereferencer;
import io.apicurio.registry.json.content.extract.JsonContentExtractor;
import io.apicurio.registry.json.content.extract.JsonSchemaStructuredContentExtractor;
import io.apicurio.registry.json.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.validity.JsonSchemaContentValidator;
import io.apicurio.registry.kconnect.content.canon.KafkaConnectContentCanonicalizer;
import io.apicurio.registry.kconnect.rules.validity.KafkaConnectContentValidator;
import io.apicurio.registry.openapi.content.OpenApiContentAccepter;
import io.apicurio.registry.openapi.content.canon.OpenApiContentCanonicalizer;
import io.apicurio.registry.openapi.content.dereference.OpenApiDereferencer;
import io.apicurio.registry.openapi.content.extract.OpenApiContentExtractor;
import io.apicurio.registry.openapi.content.extract.OpenApiStructuredContentExtractor;
import io.apicurio.registry.openapi.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.openapi.rules.compatibility.OpenApiCompatibilityChecker;
import io.apicurio.registry.openapi.rules.validity.OpenApiContentValidator;
import io.apicurio.registry.openrpc.content.OpenRpcContentAccepter;
import io.apicurio.registry.openrpc.content.canon.OpenRpcContentCanonicalizer;
import io.apicurio.registry.openrpc.content.dereference.OpenRpcDereferencer;
import io.apicurio.registry.openrpc.content.extract.OpenRpcContentExtractor;
import io.apicurio.registry.openrpc.content.refs.OpenRpcReferenceFinder;
import io.apicurio.registry.openrpc.rules.validity.OpenRpcContentValidator;
import io.apicurio.registry.protobuf.content.ProtobufContentAccepter;
import io.apicurio.registry.protobuf.content.canon.ProtobufContentCanonicalizer;
import io.apicurio.registry.protobuf.content.dereference.ProtobufDereferencer;
import io.apicurio.registry.protobuf.content.extract.ProtobufStructuredContentExtractor;
import io.apicurio.registry.protobuf.content.refs.ProtobufReferenceFinder;
import io.apicurio.registry.protobuf.rules.compatibility.ProtobufCompatibilityChecker;
import io.apicurio.registry.protobuf.rules.validity.ProtobufContentValidator;
import io.apicurio.registry.rules.compatibility.AgentCardCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.McpToolCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.ModelSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.PromptTemplateCompatibilityChecker;
import io.apicurio.registry.rules.validity.AgentCardContentValidator;
import io.apicurio.registry.rules.validity.McpToolContentValidator;
import io.apicurio.registry.rules.validity.ModelSchemaContentValidator;
import io.apicurio.registry.rules.validity.OdcsContractContentValidator;
import io.apicurio.registry.rules.validity.PromptTemplateContentValidator;
import io.apicurio.registry.thrift.content.ThriftContentAccepter;
import io.apicurio.registry.thrift.content.canon.ThriftContentCanonicalizer;
import io.apicurio.registry.thrift.content.extract.ThriftStructuredContentExtractor;
import io.apicurio.registry.thrift.rules.validity.ThriftContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.wsdl.content.WsdlContentAccepter;
import io.apicurio.registry.wsdl.content.extract.WsdlOrXsdContentExtractor;
import io.apicurio.registry.wsdl.content.extract.WsdlStructuredContentExtractor;
import io.apicurio.registry.wsdl.rules.validity.WsdlContentValidator;
import io.apicurio.registry.xml.content.XmlContentAccepter;
import io.apicurio.registry.xml.content.canon.XmlContentCanonicalizer;
import io.apicurio.registry.xml.content.extract.XmlStructuredContentExtractor;
import io.apicurio.registry.xml.rules.validity.XmlContentValidator;
import io.apicurio.registry.xsd.content.XsdContentAccepter;
import io.apicurio.registry.xsd.content.extract.XsdStructuredContentExtractor;
import io.apicurio.registry.xsd.rules.compatibility.XsdCompatibilityChecker;
import io.apicurio.registry.xsd.rules.validity.XsdContentValidator;

/**
 * Registry of built-in {@link ArtifactTypeUtilProvider} implementations used by the schema-util module.
 * <p>
 * Each entry maps a standard {@link ArtifactType} to the content handling utilities that know how to
 * accept, validate, canonicalize, extract, dereference, and resolve references for that artifact type.
 * </p>
 */
public class StandardArtifactTypeProviderRegistry {

    private static final Map<String, ProviderConfig> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put(ArtifactType.PROTOBUF, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_PROTOBUF))
                .accepter(ProtobufContentAccepter::new)
                .compatibilityChecker(ProtobufCompatibilityChecker::new)
                .canonicalizer(ProtobufContentCanonicalizer::new)
                .validator(ProtobufContentValidator::new)
                .dereferencer(ProtobufDereferencer::new)
                .referenceFinder(ProtobufReferenceFinder::new)
                .structuredContentExtractor(ProtobufStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.OPENAPI, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML))
                .accepter(OpenApiContentAccepter::new)
                .compatibilityChecker(OpenApiCompatibilityChecker::new)
                .canonicalizer(OpenApiContentCanonicalizer::new)
                .validator(OpenApiContentValidator::new)
                .extractor(OpenApiContentExtractor::new)
                .dereferencer(OpenApiDereferencer::new)
                .referenceFinder(OpenApiReferenceFinder::new)
                .structuredContentExtractor(OpenApiStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.ASYNCAPI, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML))
                .accepter(AsyncApiContentAccepter::new)
                .canonicalizer(AsyncApiContentCanonicalizer::new)
                .validator(AsyncApiContentValidator::new)
                .extractor(AsyncApiContentExtractor::new)
                .dereferencer(AsyncApiDereferencer::new)
                .referenceFinder(AsyncApiReferenceFinder::new)
                .structuredContentExtractor(AsyncApiStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.JSON, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(JsonSchemaContentAccepter::new)
                .compatibilityChecker(JsonSchemaCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(JsonSchemaContentValidator::new)
                .extractor(JsonContentExtractor::new)
                .dereferencer(JsonSchemaDereferencer::new)
                .referenceFinder(JsonSchemaReferenceFinder::new)
                .structuredContentExtractor(JsonSchemaStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.AVRO, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(AvroContentAccepter::new)
                .compatibilityChecker(AvroCompatibilityChecker::new)
                .canonicalizer(EnhancedAvroContentCanonicalizer::new)
                .validator(AvroContentValidator::new)
                .extractor(AvroContentExtractor::new)
                .dereferencer(AvroDereferencer::new)
                .referenceFinder(AvroReferenceFinder::new)
                .referenceArtifactIdentifierExtractor(AvroReferenceArtifactIdentifierExtractor::new)
                .structuredContentExtractor(AvroStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.GRAPHQL, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_GRAPHQL))
                .accepter(GraphQLContentAccepter::new)
                .canonicalizer(GraphQLContentCanonicalizer::new)
                .validator(GraphQLContentValidator::new)
                .structuredContentExtractor(GraphQLStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.KCONNECT, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .canonicalizer(KafkaConnectContentCanonicalizer::new)
                .validator(KafkaConnectContentValidator::new)
                .build());
        PROVIDERS.put(ArtifactType.WSDL, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_XML))
                .accepter(WsdlContentAccepter::new)
                .canonicalizer(XmlContentCanonicalizer::new)
                .validator(WsdlContentValidator::new)
                .extractor(WsdlOrXsdContentExtractor::new)
                .structuredContentExtractor(WsdlStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.XSD, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_XML))
                .accepter(XsdContentAccepter::new)
                .compatibilityChecker(XsdCompatibilityChecker::new)
                .canonicalizer(XmlContentCanonicalizer::new)
                .validator(XsdContentValidator::new)
                .extractor(WsdlOrXsdContentExtractor::new)
                .structuredContentExtractor(XsdStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.XML, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_XML))
                .accepter(XmlContentAccepter::new)
                .canonicalizer(XmlContentCanonicalizer::new)
                .validator(XmlContentValidator::new)
                .structuredContentExtractor(XmlStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.AGENT_CARD, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(AgentCardContentAccepter::new)
                .compatibilityChecker(AgentCardCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(AgentCardContentValidator::new)
                .extractor(AgentCardContentExtractor::new)
                .structuredContentExtractor(AgentCardStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.MCP_TOOL, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(McpToolContentAccepter::new)
                .compatibilityChecker(McpToolCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(McpToolContentValidator::new)
                .extractor(McpToolContentExtractor::new)
                .structuredContentExtractor(McpToolStructuredContentExtractor::new)
                .build());
        PROVIDERS.put(ArtifactType.ICEBERG_TABLE, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(IcebergTableContentAccepter::new)
                .compatibilityChecker(IcebergCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(() -> new IcebergContentValidator(true))
                .extractor(() -> new IcebergContentExtractor(true))
                .structuredContentExtractor(() -> new IcebergStructuredContentExtractor(true))
                .build());
        PROVIDERS.put(ArtifactType.ICEBERG_VIEW, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(IcebergViewContentAccepter::new)
                .compatibilityChecker(IcebergCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(() -> new IcebergContentValidator(false))
                .extractor(() -> new IcebergContentExtractor(false))
                .structuredContentExtractor(() -> new IcebergStructuredContentExtractor(false))
                .build());
        PROVIDERS.put(ArtifactType.OPENRPC, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML))
                .accepter(OpenRpcContentAccepter::new)
                .canonicalizer(OpenRpcContentCanonicalizer::new)
                .validator(OpenRpcContentValidator::new)
                .extractor(OpenRpcContentExtractor::new)
                .dereferencer(OpenRpcDereferencer::new)
                .referenceFinder(OpenRpcReferenceFinder::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.MODEL_SCHEMA, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML))
                .accepter(ModelSchemaContentAccepter::new)
                .compatibilityChecker(ModelSchemaCompatibilityChecker::new)
                .canonicalizer(JsonContentCanonicalizer::new)
                .validator(ModelSchemaContentValidator::new)
                .extractor(ModelSchemaContentExtractor::new)
                .dereferencer(ModelSchemaDereferencer::new)
                .referenceFinder(ModelSchemaReferenceFinder::new)
                .structuredContentExtractor(ModelSchemaStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.PROMPT_TEMPLATE, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML, ContentTypes.TEXT_PROMPT_TEMPLATE))
                .accepter(PromptTemplateContentAccepter::new)
                .compatibilityChecker(PromptTemplateCompatibilityChecker::new)
                .canonicalizer(YamlContentCanonicalizer::new)
                .validator(PromptTemplateContentValidator::new)
                .extractor(PromptTemplateContentExtractor::new)
                .dereferencer(PromptTemplateDereferencer::new)
                .referenceFinder(PromptTemplateReferenceFinder::new)
                .structuredContentExtractor(PromptTemplateStructuredContentExtractor::new)
                .supportsReferencesWithContext(true)
                .build());
        PROVIDERS.put(ArtifactType.ODCS_CONTRACT, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_YAML))
                .accepter(OdcsContractContentAccepter::new)
                .canonicalizer(YamlContentCanonicalizer::new)
                .validator(OdcsContractContentValidator::new)
                .referenceFinder(OdcsContractReferenceFinder::new)
                .build());
        PROVIDERS.put(ArtifactType.THRIFT, new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_THRIFT))
                .accepter(ThriftContentAccepter::new)
                .canonicalizer(ThriftContentCanonicalizer::new)
                .validator(ThriftContentValidator::new)
                .structuredContentExtractor(ThriftStructuredContentExtractor::new)
                .build());
    }

    /**
     * Creates a fresh set of standard artifact type utility providers per factory instance.
     * <p>
     * Note: Fresh provider instances are returned per call to prevent aliasing bugs across
     * factory instances, since {@link AbstractArtifactTypeUtilProvider} lazy-caches mutable
     * component references in volatile fields.
     *
     * @return a new list of built-in provider instances in registration order
     */
    public static List<ArtifactTypeUtilProvider> createStandardProviders() {
        List<ArtifactTypeUtilProvider> providers = new ArrayList<>();
        PROVIDERS.forEach((type, config) ->
                providers.add(new ConfigurableArtifactTypeUtilProvider(type, config))
        );
        return providers;
    }

}
