package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.util.xml.DocumentBuilderAccessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * XSD Compatibility Checker that validates schema evolution according to standard compatibility rules.
 * 
 * Compatibility Rules:
 * - BACKWARD: Old data must be readable by new schema (L(old) ⊆ L(new))
 * - FORWARD: New data must be readable by old schema (L(new) ⊆ L(old))
 * - FULL: Both backward and forward compatible
 * - TRANSITIVE: Rules apply to all historical versions, not just the latest
 */
public class XsdCompatibilityChecker extends AbstractCompatibilityChecker<XsdCompatibilityChecker.XsdIncompatibility> {

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    
    @Override
    protected Set<XsdIncompatibility> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        try {
            Document existingDoc = parseXsd(existing);
            Document proposedDoc = parseXsd(proposed);
            
            XsdSchema existingSchema = new XsdSchema(existingDoc);
            XsdSchema proposedSchema = new XsdSchema(proposedDoc);
            
            Set<XsdIncompatibility> incompatibilities = new HashSet<>();
            
            // Check for breaking changes in backward compatibility
            checkElementsBackwardCompatibility(existingSchema, proposedSchema, incompatibilities);
            checkAttributesBackwardCompatibility(existingSchema, proposedSchema, incompatibilities);
            checkTypesBackwardCompatibility(existingSchema, proposedSchema, incompatibilities);
            
            return incompatibilities;
        } catch (Exception ex) {
            throw new UnprocessableSchemaException(
                    "Could not execute compatibility rule on invalid XSD schema", ex);
        }
    }

    @Override
    protected CompatibilityDifference transform(XsdIncompatibility original) {
        return new SimpleCompatibilityDifference(original.getMessage(), original.getContext());
    }

    private Document parseXsd(String xsdContent) throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(xsdContent.getBytes(StandardCharsets.UTF_8));
        return DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
    }

    /**
     * Check backward compatibility for elements
     */
    private void checkElementsBackwardCompatibility(XsdSchema existing, XsdSchema proposed,
            Set<XsdIncompatibility> incompatibilities) {
        // Check that all existing required elements are still present in proposed
        for (XsdElement existingElement : existing.getElements()) {
            XsdElement proposedElement = proposed.getElement(existingElement.getName());
            
            if (proposedElement == null) {
                // Element was removed - this is backward incompatible even if optional
                // because old data may have this element
                incompatibilities.add(new XsdIncompatibility(
                    "Element '" + existingElement.getName() + "' was removed",
                    "/element[" + existingElement.getName() + "]"
                ));
            } else {
                // Element exists in both schemas, check for incompatible changes
                checkElementChanges(existingElement, proposedElement, incompatibilities);
            }
        }
        
        // Check for new required elements in proposed (backward incompatible)
        for (XsdElement proposedElement : proposed.getElements()) {
            XsdElement existingElement = existing.getElement(proposedElement.getName());
            if (existingElement == null && proposedElement.isRequired()) {
                incompatibilities.add(new XsdIncompatibility(
                    "New required element '" + proposedElement.getName() + "' was added",
                    "/element[" + proposedElement.getName() + "]"
                ));
            }
        }
    }

    private void checkElementChanges(XsdElement existing, XsdElement proposed,
            Set<XsdIncompatibility> incompatibilities) {
        String elementPath = "/element[" + existing.getName() + "]";
        
        // Check if minOccurs increased (making it more restrictive)
        if (proposed.getMinOccurs() > existing.getMinOccurs()) {
            incompatibilities.add(new XsdIncompatibility(
                "Element '" + existing.getName() + "' minOccurs increased from " +
                existing.getMinOccurs() + " to " + proposed.getMinOccurs(),
                elementPath
            ));
        }
        
        // Check if maxOccurs decreased (making it more restrictive)
        if (existing.getMaxOccurs() != -1 && proposed.getMaxOccurs() != -1 &&
            proposed.getMaxOccurs() < existing.getMaxOccurs()) {
            incompatibilities.add(new XsdIncompatibility(
                "Element '" + existing.getName() + "' maxOccurs decreased from " +
                existing.getMaxOccurs() + " to " + proposed.getMaxOccurs(),
                elementPath
            ));
        } else if (existing.getMaxOccurs() == -1 && proposed.getMaxOccurs() != -1) {
            incompatibilities.add(new XsdIncompatibility(
                "Element '" + existing.getName() + "' maxOccurs changed from unbounded to " +
                proposed.getMaxOccurs(),
                elementPath
            ));
        }
        
        // Check type compatibility (narrowing)
        if (!isTypeCompatible(existing.getType(), proposed.getType(), false)) {
            incompatibilities.add(new XsdIncompatibility(
                "Element '" + existing.getName() + "' type changed from " +
                existing.getType() + " to " + proposed.getType() + " in an incompatible way",
                elementPath
            ));
        }
        
        // Check if nillable was removed
        if (existing.isNillable() && !proposed.isNillable()) {
            incompatibilities.add(new XsdIncompatibility(
                "Element '" + existing.getName() + "' is no longer nillable",
                elementPath
            ));
        }
    }

    /**
     * Check backward compatibility for attributes
     */
    private void checkAttributesBackwardCompatibility(XsdSchema existing, XsdSchema proposed,
            Set<XsdIncompatibility> incompatibilities) {
        for (XsdAttribute existingAttr : existing.getAttributes()) {
            XsdAttribute proposedAttr = proposed.getAttribute(existingAttr.getName());
            
            if (proposedAttr == null) {
                // Attribute was removed - backward incompatible even if optional
                // because old data may have this attribute
                incompatibilities.add(new XsdIncompatibility(
                    "Attribute '" + existingAttr.getName() + "' was removed",
                    "/attribute[" + existingAttr.getName() + "]"
                ));
            } else {
                checkAttributeChanges(existingAttr, proposedAttr, incompatibilities);
            }
        }
        
        // Check for new required attributes in proposed (backward incompatible)
        for (XsdAttribute proposedAttr : proposed.getAttributes()) {
            XsdAttribute existingAttr = existing.getAttribute(proposedAttr.getName());
            if (existingAttr == null && proposedAttr.isRequired()) {
                incompatibilities.add(new XsdIncompatibility(
                    "New required attribute '" + proposedAttr.getName() + "' was added",
                    "/attribute[" + proposedAttr.getName() + "]"
                ));
            }
        }
    }

    private void checkAttributeChanges(XsdAttribute existing, XsdAttribute proposed,
            Set<XsdIncompatibility> incompatibilities) {
        String attrPath = "/attribute[" + existing.getName() + "]";
        
        // Check if optional became required
        if (!existing.isRequired() && proposed.isRequired()) {
            // This is actually OK for backward (new schema can read old data)
            // but NOT OK for forward (old schema cannot read new data)
            // This will be caught in forward compatibility check
        }
        
        // Check if required became optional - this is OK for backward
        
        // Check type compatibility
        if (!isTypeCompatible(existing.getType(), proposed.getType(), false)) {
            incompatibilities.add(new XsdIncompatibility(
                "Attribute '" + existing.getName() + "' type changed from " +
                existing.getType() + " to " + proposed.getType() + " in an incompatible way",
                attrPath
            ));
        }
    }

    /**
     * Check backward compatibility for types
     */
    private void checkTypesBackwardCompatibility(XsdSchema existing, XsdSchema proposed,
            Set<XsdIncompatibility> incompatibilities) {
        for (XsdType existingType : existing.getTypes()) {
            XsdType proposedType = proposed.getType(existingType.getName());
            
            if (proposedType == null) {
                incompatibilities.add(new XsdIncompatibility(
                    "Type '" + existingType.getName() + "' was removed",
                    "/type[" + existingType.getName() + "]"
                ));
            } else {
                checkTypeChanges(existingType, proposedType, incompatibilities);
            }
        }
    }

    private void checkTypeChanges(XsdType existing, XsdType proposed,
            Set<XsdIncompatibility> incompatibilities) {
        String typePath = "/type[" + existing.getName() + "]";
        
        // Check restriction changes
        if (existing.getRestriction() != null && proposed.getRestriction() != null) {
            checkRestrictionChanges(existing.getName(), existing.getRestriction(),
                proposed.getRestriction(), incompatibilities, typePath);
        }
        
        // Check enumeration changes
        if (!existing.getEnumerationValues().isEmpty() || !proposed.getEnumerationValues().isEmpty()) {
            checkEnumerationChanges(existing.getName(), existing.getEnumerationValues(),
                proposed.getEnumerationValues(), incompatibilities, typePath);
        }
    }

    private void checkRestrictionChanges(String typeName, XsdRestriction existing,
            XsdRestriction proposed, Set<XsdIncompatibility> incompatibilities, String context) {
        // Check if numeric ranges were tightened
        if (existing.getMinInclusive() != null && proposed.getMinInclusive() != null) {
            if (compareNumeric(proposed.getMinInclusive(), existing.getMinInclusive()) > 0) {
                incompatibilities.add(new XsdIncompatibility(
                    "Type '" + typeName + "' minInclusive increased from " +
                    existing.getMinInclusive() + " to " + proposed.getMinInclusive(),
                    context
                ));
            }
        }
        
        if (existing.getMaxInclusive() != null && proposed.getMaxInclusive() != null) {
            if (compareNumeric(proposed.getMaxInclusive(), existing.getMaxInclusive()) < 0) {
                incompatibilities.add(new XsdIncompatibility(
                    "Type '" + typeName + "' maxInclusive decreased from " +
                    existing.getMaxInclusive() + " to " + proposed.getMaxInclusive(),
                    context
                ));
            }
        }
        
        // Check if minLength increased
        if (proposed.getMinLength() != null && existing.getMinLength() != null &&
            proposed.getMinLength() > existing.getMinLength()) {
            incompatibilities.add(new XsdIncompatibility(
                "Type '" + typeName + "' minLength increased from " +
                existing.getMinLength() + " to " + proposed.getMinLength(),
                context
            ));
        }
        
        // Check if maxLength decreased
        if (proposed.getMaxLength() != null && existing.getMaxLength() != null &&
            proposed.getMaxLength() < existing.getMaxLength()) {
            incompatibilities.add(new XsdIncompatibility(
                "Type '" + typeName + "' maxLength decreased from " +
                existing.getMaxLength() + " to " + proposed.getMaxLength(),
                context
            ));
        }
        
        // Check if pattern was tightened
        if (existing.getPattern() != null && proposed.getPattern() != null &&
            !existing.getPattern().equals(proposed.getPattern())) {
            // Pattern change is incompatible unless we can prove new pattern is broader
            incompatibilities.add(new XsdIncompatibility(
                "Type '" + typeName + "' pattern changed from '" +
                existing.getPattern() + "' to '" + proposed.getPattern() + "'",
                context
            ));
        }
    }

    private void checkEnumerationChanges(String typeName, Set<String> existingValues,
            Set<String> proposedValues, Set<XsdIncompatibility> incompatibilities, String context) {
        // For backward compatibility, removing enum values is a problem
        Set<String> removedValues = new HashSet<>(existingValues);
        removedValues.removeAll(proposedValues);
        
        if (!removedValues.isEmpty()) {
            incompatibilities.add(new XsdIncompatibility(
                "Type '" + typeName + "' enumeration values removed: " +
                String.join(", ", removedValues),
                context
            ));
        }
    }

    private boolean isTypeCompatible(String existingType, String proposedType, boolean strict) {
        if (existingType.equals(proposedType)) {
            return true;
        }
        
        // Check if proposed type is a broader version of existing type
        // This is a simplified check - a full implementation would need type hierarchy analysis
        return false;
    }

    private int compareNumeric(String val1, String val2) {
        try {
            BigDecimal bd1 = new BigDecimal(val1);
            BigDecimal bd2 = new BigDecimal(val2);
            return bd1.compareTo(bd2);
        } catch (NumberFormatException e) {
            return val1.compareTo(val2);
        }
    }

    /**
     * Represents an XSD incompatibility
     */
    public static class XsdIncompatibility {
        private final String message;
        private final String context;

        public XsdIncompatibility(String message, String context) {
            this.message = message;
            this.context = context;
        }

        public String getMessage() {
            return message;
        }

        public String getContext() {
            return context;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            XsdIncompatibility that = (XsdIncompatibility) o;
            return Objects.equals(message, that.message) && Objects.equals(context, that.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, context);
        }
    }

    /**
     * Internal representation of an XSD schema
     */
    private static class XsdSchema {
        private final Document document;
        private final Map<String, XsdElement> elements = new HashMap<>();
        private final Map<String, XsdAttribute> attributes = new HashMap<>();
        private final Map<String, XsdType> types = new HashMap<>();

        public XsdSchema(Document document) {
            this.document = document;
            parseSchema();
        }

        private void parseSchema() {
            Element root = document.getDocumentElement();
            NodeList children = root.getChildNodes();
            
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                
                Element element = (Element) child;
                String localName = element.getLocalName();
                
                if ("element".equals(localName)) {
                    XsdElement xsdElement = parseElement(element);
                    if (xsdElement.getName() != null) {
                        elements.put(xsdElement.getName(), xsdElement);
                    }
                } else if ("attribute".equals(localName)) {
                    XsdAttribute xsdAttribute = parseAttribute(element);
                    if (xsdAttribute.getName() != null) {
                        attributes.put(xsdAttribute.getName(), xsdAttribute);
                    }
                } else if ("simpleType".equals(localName) || "complexType".equals(localName)) {
                    XsdType xsdType = parseType(element);
                    if (xsdType.getName() != null) {
                        types.put(xsdType.getName(), xsdType);
                    }
                    // Also parse elements and attributes from within the complexType
                    parseComplexTypeContent(element);
                }
            }
        }
        
        private void parseComplexTypeContent(Element complexTypeElement) {
            // Parse elements from sequence, choice, or all
            NodeList sequences = complexTypeElement.getElementsByTagNameNS(XSD_NS, "sequence");
            for (int i = 0; i < sequences.getLength(); i++) {
                Element sequence = (Element) sequences.item(i);
                parseElementsFromContainer(sequence);
            }
            
            NodeList choices = complexTypeElement.getElementsByTagNameNS(XSD_NS, "choice");
            for (int i = 0; i < choices.getLength(); i++) {
                Element choice = (Element) choices.item(i);
                parseElementsFromContainer(choice);
            }
            
            NodeList alls = complexTypeElement.getElementsByTagNameNS(XSD_NS, "all");
            for (int i = 0; i < alls.getLength(); i++) {
                Element all = (Element) alls.item(i);
                parseElementsFromContainer(all);
            }
            
            // Parse attributes
            NodeList attrs = complexTypeElement.getElementsByTagNameNS(XSD_NS, "attribute");
            for (int i = 0; i < attrs.getLength(); i++) {
                Element attrElement = (Element) attrs.item(i);
                XsdAttribute xsdAttribute = parseAttribute(attrElement);
                if (xsdAttribute.getName() != null) {
                    attributes.put(xsdAttribute.getName(), xsdAttribute);
                }
            }
        }
        
        private void parseElementsFromContainer(Element container) {
            NodeList children = container.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) child;
                if ("element".equals(element.getLocalName())) {
                    XsdElement xsdElement = parseElement(element);
                    if (xsdElement.getName() != null) {
                        elements.put(xsdElement.getName(), xsdElement);
                    }
                }
            }
        }

        private XsdElement parseElement(Element element) {
            String name = element.getAttribute("name");
            String type = element.getAttribute("type");
            int minOccurs = parseOccurs(element.getAttribute("minOccurs"), 1);
            int maxOccurs = parseOccurs(element.getAttribute("maxOccurs"), 1);
            boolean nillable = "true".equals(element.getAttribute("nillable"));
            
            return new XsdElement(name, type, minOccurs, maxOccurs, nillable);
        }

        private XsdAttribute parseAttribute(Element element) {
            String name = element.getAttribute("name");
            String type = element.getAttribute("type");
            String use = element.getAttribute("use");
            boolean required = "required".equals(use);
            
            return new XsdAttribute(name, type, required);
        }

        private XsdType parseType(Element element) {
            String name = element.getAttribute("name");
            XsdType type = new XsdType(name);
            
            // Parse restrictions
            NodeList restrictions = element.getElementsByTagNameNS(XSD_NS, "restriction");
            if (restrictions.getLength() > 0) {
                Element restriction = (Element) restrictions.item(0);
                type.setRestriction(parseRestriction(restriction));
            }
            
            // Parse enumerations
            NodeList enumerations = element.getElementsByTagNameNS(XSD_NS, "enumeration");
            for (int i = 0; i < enumerations.getLength(); i++) {
                Element enumElement = (Element) enumerations.item(i);
                String value = enumElement.getAttribute("value");
                if (value != null && !value.isEmpty()) {
                    type.addEnumerationValue(value);
                }
            }
            
            return type;
        }

        private XsdRestriction parseRestriction(Element restriction) {
            XsdRestriction xsdRestriction = new XsdRestriction();
            
            NodeList children = restriction.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                
                Element element = (Element) child;
                String localName = element.getLocalName();
                String value = element.getAttribute("value");
                
                switch (localName) {
                    case "minInclusive":
                        xsdRestriction.setMinInclusive(value);
                        break;
                    case "maxInclusive":
                        xsdRestriction.setMaxInclusive(value);
                        break;
                    case "minLength":
                        xsdRestriction.setMinLength(Integer.parseInt(value));
                        break;
                    case "maxLength":
                        xsdRestriction.setMaxLength(Integer.parseInt(value));
                        break;
                    case "pattern":
                        xsdRestriction.setPattern(value);
                        break;
                }
            }
            
            return xsdRestriction;
        }

        private int parseOccurs(String value, int defaultValue) {
            if (value == null || value.isEmpty()) {
                return defaultValue;
            }
            if ("unbounded".equals(value)) {
                return -1;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        public Collection<XsdElement> getElements() {
            return elements.values();
        }

        public XsdElement getElement(String name) {
            return elements.get(name);
        }

        public Collection<XsdAttribute> getAttributes() {
            return attributes.values();
        }

        public XsdAttribute getAttribute(String name) {
            return attributes.get(name);
        }

        public Collection<XsdType> getTypes() {
            return types.values();
        }

        public XsdType getType(String name) {
            return types.get(name);
        }
    }

    private static class XsdElement {
        private final String name;
        private final String type;
        private final int minOccurs;
        private final int maxOccurs;
        private final boolean nillable;

        public XsdElement(String name, String type, int minOccurs, int maxOccurs, boolean nillable) {
            this.name = name;
            this.type = type;
            this.minOccurs = minOccurs;
            this.maxOccurs = maxOccurs;
            this.nillable = nillable;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public int getMinOccurs() {
            return minOccurs;
        }

        public int getMaxOccurs() {
            return maxOccurs;
        }

        public boolean isRequired() {
            return minOccurs > 0;
        }

        public boolean isNillable() {
            return nillable;
        }
    }

    private static class XsdAttribute {
        private final String name;
        private final String type;
        private final boolean required;

        public XsdAttribute(String name, String type, boolean required) {
            this.name = name;
            this.type = type;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }
    }

    private static class XsdType {
        private final String name;
        private XsdRestriction restriction;
        private final Set<String> enumerationValues = new HashSet<>();

        public XsdType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public XsdRestriction getRestriction() {
            return restriction;
        }

        public void setRestriction(XsdRestriction restriction) {
            this.restriction = restriction;
        }

        public Set<String> getEnumerationValues() {
            return enumerationValues;
        }

        public void addEnumerationValue(String value) {
            enumerationValues.add(value);
        }
    }

    private static class XsdRestriction {
        private String minInclusive;
        private String maxInclusive;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;

        public String getMinInclusive() {
            return minInclusive;
        }

        public void setMinInclusive(String minInclusive) {
            this.minInclusive = minInclusive;
        }

        public String getMaxInclusive() {
            return maxInclusive;
        }

        public void setMaxInclusive(String maxInclusive) {
            this.maxInclusive = maxInclusive;
        }

        public Integer getMinLength() {
            return minLength;
        }

        public void setMinLength(Integer minLength) {
            this.minLength = minLength;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
