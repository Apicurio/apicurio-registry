package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.validation.SchemaFactory;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compiles XML Schemas, resolving imports/includes only from registry references.
 */
public final class XsdSchemaCompiler {

    private XsdSchemaCompiler() {
    }

    /**
     * Thrown when one or more referenced locations are not present in the resolved references.
     */
    public static class UnresolvedReferencesException extends Exception {
        private static final long serialVersionUID = 1L;
        private final Set<String> locations;

        UnresolvedReferencesException(Set<String> locations) {
            super("Unresolved reference(s): " + String.join(", ", locations));
            this.locations = locations;
        }

        /**
         * Returns the unresolved locations.
         *
         * @return unresolved locations in encounter order
         */
        public Set<String> getLocations() {
            return locations;
        }
    }

    /**
     * Compiles the given schema sources together, resolving locations only from the given references.
     *
     * @param sources schema sources (give each a systemId)
     * @param resolvedReferences map of reference name to content; may be null
     * @throws UnresolvedReferencesException if any location was not mapped (checked before schema errors)
     * @throws SAXException if the schema is invalid
     */
    public static void compile(Source[] sources, Map<String, TypedContent> resolvedReferences)
            throws UnresolvedReferencesException, SAXException {
        compile(sources, resolvedReferences, null, null);
    }

    /**
     * Compiles the given schema sources together, resolving locations from the given in-memory inline
     * documents first and otherwise only from the given references.
     *
     * @param sources schema sources (give each a systemId)
     * @param resolvedReferences map of reference name to content; may be null
     * @param inlineDocuments map of synthetic system id to schema text; may be null
     * @param inlineNamespaces map of target namespace to synthetic system id of its inline document, used
     *            for namespace-only imports; may be null
     * @throws UnresolvedReferencesException if any location was not mapped (checked before schema errors)
     * @throws SAXException if the schema is invalid
     * @see MappedReferenceResourceResolver#MappedReferenceResourceResolver(Map, Map, Map)
     */
    public static void compile(Source[] sources, Map<String, TypedContent> resolvedReferences,
            Map<String, String> inlineDocuments, Map<String, String> inlineNamespaces)
            throws UnresolvedReferencesException, SAXException {
        MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(resolvedReferences,
                inlineDocuments, inlineNamespaces);
        SchemaFactory factory = SchemaFactoryAccessor.newSchemaFactory();
        factory.setResourceResolver(resolver);
        SAXException schemaError = null;
        try {
            factory.newSchema(sources);
        } catch (SAXException e) {
            schemaError = e;
        }
        Set<String> unresolved = resolver.getUnresolved();
        if (!unresolved.isEmpty()) {
            throw new UnresolvedReferencesException(unresolved);
        }
        if (schemaError != null) {
            throw schemaError;
        }
    }

    /**
     * Builds a validity rule violation listing the given unresolved locations, one cause per location.
     *
     * @param level the validity level being applied
     * @param locations unresolved locations
     * @param artifactLabel human-readable artifact kind, e.g. "XSD Schema"
     * @return the rule violation exception
     */
    public static RuleViolationException toRuleViolation(ValidityLevel level, Set<String> locations,
            String artifactLabel) {
        Set<RuleViolation> causes = locations.stream()
                .map(l -> new RuleViolation("Unresolved reference: " + l, l))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RuleViolationException("Unresolved reference(s) in " + artifactLabel + " artifact.",
                RuleType.VALIDITY, level.name(), causes);
    }
}
