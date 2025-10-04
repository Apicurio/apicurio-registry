# XSD Compatibility Checker Implementation

## Overview
This document describes the implementation of XSD (XML Schema Definition) compatibility checking for the Apicurio Registry, following standard schema evolution compatibility rules.

## Implementation Details

### Core Classes

#### 1. `XsdCompatibilityChecker`
Location: `src/main/java/io/apicurio/registry/rules/compatibility/XsdCompatibilityChecker.java`

This is the main compatibility checker class that extends `AbstractCompatibilityChecker` and implements XSD-specific compatibility rules.

**Key Features:**
- Parses XSD schemas using DOM API
- Extracts elements, attributes, and types from complex type definitions
- Checks backward, forward, and full compatibility
- Supports transitive compatibility checking

### Compatibility Rules Implemented

#### BACKWARD Compatibility
**Rule:** Old data must be readable by the new schema (L(old) ⊆ L(new))

**Safe Changes:**
- ✅ Add new optional elements (`minOccurs="0"`)
- ✅ Add new optional attributes (`use="optional"`)
- ✅ Decrease `minOccurs` on existing elements
- ✅ Increase `maxOccurs` on existing elements
- ✅ Change required attribute to optional
- ✅ Add enumeration values to restrictions
- ✅ Widen numeric ranges (increase `maxInclusive`, decrease `minInclusive`)

**Breaking Changes:**
- ❌ Add new required elements
- ❌ Add new required attributes
- ❌ Remove any elements (even optional ones)
- ❌ Remove any attributes (even optional ones)
- ❌ Increase `minOccurs` on existing elements
- ❌ Decrease `maxOccurs` on existing elements
- ❌ Change unbounded to bounded `maxOccurs`
- ❌ Change element/attribute types
- ❌ Remove `nillable` from elements
- ❌ Remove enumeration values
- ❌ Narrow numeric ranges

#### FORWARD Compatibility
**Rule:** New data must be readable by the old schema (L(new) ⊆ L(old))

**Safe Changes:**
- ✅ Remove optional elements/attributes
- ✅ Change optional to required
- ✅ Increase `minOccurs` on existing elements
- ✅ Decrease `maxOccurs` on existing elements
- ✅ Narrow types and facets
- ✅ Add constraints (assertions, keys, uniqueness)
- ✅ Remove enumeration values
- ✅ Narrow numeric ranges

**Breaking Changes:**
- ❌ Add new elements (unless old schema has wildcards)
- ❌ Add new attributes (unless old schema has wildcards)
- ❌ Relax types or facets
- ❌ Decrease `minOccurs`
- ❌ Increase `maxOccurs`
- ❌ Remove required elements/attributes

#### FULL Compatibility
Both backward AND forward compatible (most restrictive)

#### TRANSITIVE Modes
Apply the same rules across the entire version history, not just the most recent version.

### Internal Classes

#### `XsdSchema`
Represents a parsed XSD schema with:
- **Elements**: Extracted from all `<xs:element>` declarations
- **Attributes**: Extracted from all `<xs:attribute>` declarations  
- **Types**: Both simple and complex type definitions

**Parsing Strategy:**
- Parses top-level schema elements
- Recursively extracts elements and attributes from `<xs:complexType>` definitions
- Handles `<xs:sequence>`, `<xs:choice>`, and `<xs:all>` containers
- Extracts restrictions and their facets (min/max, patterns, enumerations)

#### `XsdElement`
Represents an XSD element with:
- `name`: Element name
- `type`: Type reference
- `minOccurs`: Minimum occurrences (default: 1)
- `maxOccurs`: Maximum occurrences (-1 for unbounded)
- `nillable`: Whether the element can be nil

#### `XsdAttribute`
Represents an XSD attribute with:
- `name`: Attribute name
- `type`: Type reference
- `required`: Whether the attribute is required (`use="required"`)

#### `XsdType`
Represents an XSD type definition with:
- `name`: Type name
- `restriction`: Associated restrictions (if any)
- `enumerationValues`: Enumeration values (for simple types)

#### `XsdRestriction`
Represents XSD facet restrictions with:
- `pattern`: Regular expression pattern
- `minInclusive`/`maxInclusive`: Numeric bounds
- `minLength`/`maxLength`: String length bounds
- `enumerationValues`: Allowed enumeration values

#### `XsdIncompatibility`
Represents a detected incompatibility with:
- `message`: Human-readable description
- `context`: XPath-like location in the schema

### Test Coverage

Location: `src/test/java/io/apicurio/registry/rules/compatibility/XsdCompatibilityCheckerTest.java`

**Test Cases:**
1. ✅ `testBackwardCompatible_AddOptionalElement` - Adding optional elements
2. ✅ `testBackwardCompatible_AddOptionalAttribute` - Adding optional attributes
3. ✅ `testBackwardCompatible_DecreaseMinOccurs` - Relaxing element occurrence
4. ✅ `testBackwardIncompatible_AddRequiredElement` - Adding required elements (should fail)
5. ✅ `testBackwardIncompatible_RemoveRequiredElement` - Removing elements (should fail)
6. ✅ `testBackwardIncompatible_IncreaseMinOccurs` - Tightening occurrence (should fail)
7. ✅ `testForwardCompatible_IncreaseMinOccurs` - Making elements more required
8. ✅ `testForwardIncompatible_AddOptionalElement` - Adding optional elements (forward incompatible)
9. ✅ `testFullCompatible` - Both backward and forward compatible
10. ✅ `testFullIncompatible` - Incompatible in both directions
11. ✅ `testBackwardTransitive` - Transitive backward compatibility
12. ✅ `testRestrictionCompatibility_AddEnumValue` - Adding enumeration values
13. ✅ `testRestrictionCompatibility_RemoveEnumValue` - Removing enumeration values

All tests pass successfully! ✨

### Integration

The XSD compatibility checker is integrated into the registry via:

**File:** `schema-util/util-provider/src/main/java/io/apicurio/registry/types/provider/XsdArtifactTypeUtilProvider.java`

Changed from:
```java
protected CompatibilityChecker createCompatibilityChecker() {
    return NoopCompatibilityChecker.INSTANCE;
}
```

To:
```java
protected CompatibilityChecker createCompatibilityChecker() {
    return new XsdCompatibilityChecker();
}
```

## Usage

The compatibility checker is automatically used by the Apicurio Registry when:
1. A user uploads a new version of an XSD artifact
2. A compatibility rule is enabled for that artifact (BACKWARD, FORWARD, FULL, or their TRANSITIVE variants)
3. The registry validates the new schema against existing versions

## Dependencies

The implementation uses only standard Java libraries:
- `org.w3c.dom` for XML/XSD parsing
- `java.math.BigDecimal` for numeric comparisons
- Standard Java collections

## Future Enhancements

Potential improvements for future iterations:

1. **Advanced Type Analysis**: Deeper inspection of type hierarchies and inheritance
2. **Wildcard Support**: Proper handling of `<xs:any>` and `<xs:anyAttribute>`
3. **Import/Include Handling**: Support for multi-file schemas with imports
4. **Substitution Groups**: Analysis of substitution group compatibility
5. **XSD 1.1 Features**: Support for assertions, open content, and other XSD 1.1 features
6. **Performance Optimization**: Caching parsed schemas for repeated checks

## References

- XSD Specification: https://www.w3.org/TR/xmlschema11-1/
- Schema Evolution Best Practices
- Apicurio Registry Documentation
