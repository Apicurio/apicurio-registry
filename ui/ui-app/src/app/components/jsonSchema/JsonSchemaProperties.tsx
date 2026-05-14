import React, { FunctionComponent, useState } from "react";
import "./JsonSchemaProperties.css";
import {
    ExpandableSection,
    Flex,
    FlexItem,
    Label,
    LabelGroup
} from "@patternfly/react-core";

/**
 * Properties for the JsonSchemaProperties component.
 */
export type JsonSchemaPropertiesProps = {
    schema: any;
    depth?: number;
};

/**
 * Returns a display string for the type of a JSON Schema property.
 */
const getTypeLabel = (schema: any): string => {
    if (!schema) {
        return "any";
    }
    if (schema.type === "array") {
        if (schema.items) {
            const itemType = getTypeLabel(schema.items);
            return `${itemType}[]`;
        }
        return "array";
    }
    if (schema.type) {
        return Array.isArray(schema.type) ? schema.type.join(" | ") : schema.type;
    }
    if (schema.oneOf) {
        return "oneOf";
    }
    if (schema.anyOf) {
        return "anyOf";
    }
    if (schema.allOf) {
        return "allOf";
    }
    if (schema.$ref) {
        return "$ref";
    }
    return "any";
};

/**
 * Returns the color for a type label badge.
 */
const getTypeColor = (type: string): "blue" | "green" | "orange" | "purple" | "teal" | "grey" => {
    if (type === "string") return "green";
    if (type === "number" || type === "integer") return "blue";
    if (type === "boolean") return "teal";
    if (type === "object") return "orange";
    if (type.endsWith("[]") || type === "array") return "purple";
    return "grey";
};

/**
 * Collects constraint labels for a schema property.
 */
const getConstraints = (schema: any): string[] => {
    const constraints: string[] = [];
    if (schema.minLength !== undefined) constraints.push(`minLength: ${schema.minLength}`);
    if (schema.maxLength !== undefined) constraints.push(`maxLength: ${schema.maxLength}`);
    if (schema.minimum !== undefined) constraints.push(`min: ${schema.minimum}`);
    if (schema.maximum !== undefined) constraints.push(`max: ${schema.maximum}`);
    if (schema.exclusiveMinimum !== undefined) constraints.push(`exclusiveMin: ${schema.exclusiveMinimum}`);
    if (schema.exclusiveMaximum !== undefined) constraints.push(`exclusiveMax: ${schema.exclusiveMaximum}`);
    if (schema.pattern) constraints.push(`pattern: ${schema.pattern}`);
    if (schema.format) constraints.push(`format: ${schema.format}`);
    if (schema.minItems !== undefined) constraints.push(`minItems: ${schema.minItems}`);
    if (schema.maxItems !== undefined) constraints.push(`maxItems: ${schema.maxItems}`);
    if (schema.uniqueItems) constraints.push("uniqueItems");
    if (schema.multipleOf !== undefined) constraints.push(`multipleOf: ${schema.multipleOf}`);
    if (schema.readOnly) constraints.push("readOnly");
    if (schema.writeOnly) constraints.push("writeOnly");
    return constraints;
};

/**
 * Checks if a schema has nested properties that can be expanded.
 */
const hasNestedContent = (schema: any): boolean => {
    if (schema.type === "object" && schema.properties) return true;
    if (schema.type === "array" && schema.items) {
        if (schema.items.type === "object" && schema.items.properties) return true;
        if (schema.items.oneOf || schema.items.anyOf || schema.items.allOf) return true;
    }
    if (schema.oneOf || schema.anyOf || schema.allOf) return true;
    return false;
};

/**
 * Renders a single JSON Schema property row.
 */
const PropertyRow: FunctionComponent<{
    name: string;
    schema: any;
    required: boolean;
    depth: number;
}> = ({ name, schema, required, depth }) => {
    const [isExpanded, setIsExpanded] = useState(depth < 1);
    const typeLabel = getTypeLabel(schema);
    const constraints = getConstraints(schema);
    const expandable = hasNestedContent(schema);

    const renderNestedContent = (): React.ReactElement | null => {
        if (schema.type === "object" && schema.properties) {
            return (
                <div className="json-schema-nested-properties">
                    <JsonSchemaProperties schema={schema} depth={depth + 1} />
                </div>
            );
        }
        if (schema.type === "array" && schema.items) {
            if (schema.items.type === "object" && schema.items.properties) {
                return (
                    <div className="json-schema-array-items">
                        <Label color="grey" isCompact>Items:</Label>
                        <div className="json-schema-nested-properties">
                            <JsonSchemaProperties schema={schema.items} depth={depth + 1} />
                        </div>
                    </div>
                );
            }
            if (schema.items.oneOf || schema.items.anyOf || schema.items.allOf) {
                return (
                    <div className="json-schema-array-items">
                        <Label color="grey" isCompact>Items:</Label>
                        <CombinatorContent schema={schema.items} depth={depth + 1} />
                    </div>
                );
            }
        }
        if (schema.oneOf || schema.anyOf || schema.allOf) {
            return <CombinatorContent schema={schema} depth={depth + 1} />;
        }
        return null;
    };

    return (
        <div className="json-schema-property">
            <Flex className="json-schema-property-header">
                <FlexItem>
                    <span className="json-schema-property-name">{name}</span>
                </FlexItem>
                <FlexItem>
                    <Label color={getTypeColor(typeLabel)} isCompact>{typeLabel}</Label>
                </FlexItem>
                {required && (
                    <FlexItem>
                        <span className="json-schema-property-required">required</span>
                    </FlexItem>
                )}
            </Flex>
            {schema.description && (
                <div className="json-schema-property-description">{schema.description}</div>
            )}
            {constraints.length > 0 && (
                <div className="json-schema-property-constraints">
                    <LabelGroup>
                        {constraints.map((c, i) => (
                            <Label key={i} color="grey" isCompact variant="outline">{c}</Label>
                        ))}
                    </LabelGroup>
                </div>
            )}
            {schema.enum && (
                <div className="json-schema-enum-values">
                    <LabelGroup categoryName="enum">
                        {schema.enum.map((val: any, i: number) => (
                            <Label key={i} color="teal" isCompact>{String(val)}</Label>
                        ))}
                    </LabelGroup>
                </div>
            )}
            {schema.default !== undefined && (
                <div className="json-schema-property-default">
                    Default: <code>{JSON.stringify(schema.default)}</code>
                </div>
            )}
            {expandable && (
                <ExpandableSection
                    toggleText={isExpanded ? "Hide properties" : "Show properties"}
                    onToggle={(_event, expanded) => setIsExpanded(expanded)}
                    isExpanded={isExpanded}
                    className="json-schema-nested-properties"
                >
                    {renderNestedContent()}
                </ExpandableSection>
            )}
        </div>
    );
};

/**
 * Renders oneOf/anyOf/allOf combinator content.
 */
const CombinatorContent: FunctionComponent<{ schema: any; depth: number }> = ({ schema, depth }) => {
    const combinators: { key: string; label: string }[] = [
        { key: "oneOf", label: "One of" },
        { key: "anyOf", label: "Any of" },
        { key: "allOf", label: "All of" },
    ];

    return (
        <>
            {combinators.map(({ key, label }) => {
                const items = schema[key];
                if (!items || !Array.isArray(items)) return null;
                return (
                    <div key={key} className="json-schema-combinator-group">
                        <div className="json-schema-combinator-label">{label}:</div>
                        {items.map((subSchema: any, index: number) => (
                            <div key={index} className="json-schema-combinator-option">
                                {subSchema.title && (
                                    <Label color="blue" isCompact>{subSchema.title}</Label>
                                )}
                                {subSchema.description && (
                                    <div className="json-schema-property-description">
                                        {subSchema.description}
                                    </div>
                                )}
                                {subSchema.type === "object" && subSchema.properties ? (
                                    <JsonSchemaProperties schema={subSchema} depth={depth} />
                                ) : (
                                    <Flex className="json-schema-property-header" style={{ marginTop: 4 }}>
                                        <FlexItem>
                                            <Label color={getTypeColor(getTypeLabel(subSchema))} isCompact>
                                                {getTypeLabel(subSchema)}
                                            </Label>
                                        </FlexItem>
                                        {getConstraints(subSchema).length > 0 && (
                                            <FlexItem>
                                                <LabelGroup>
                                                    {getConstraints(subSchema).map((c, i) => (
                                                        <Label key={i} color="grey" isCompact variant="outline">
                                                            {c}
                                                        </Label>
                                                    ))}
                                                </LabelGroup>
                                            </FlexItem>
                                        )}
                                    </Flex>
                                )}
                            </div>
                        ))}
                    </div>
                );
            })}
        </>
    );
};

/**
 * Recursively renders JSON Schema properties as an expandable property list.
 */
export const JsonSchemaProperties: FunctionComponent<JsonSchemaPropertiesProps> = (props: JsonSchemaPropertiesProps) => {
    const { schema, depth = 0 } = props;
    const properties = schema?.properties || {};
    const requiredFields: string[] = schema?.required || [];

    const propertyNames = Object.keys(properties);
    if (propertyNames.length === 0 && !schema?.oneOf && !schema?.anyOf && !schema?.allOf) {
        return null;
    }

    return (
        <div className="json-schema-properties">
            {propertyNames.map((propName) => (
                <PropertyRow
                    key={propName}
                    name={propName}
                    schema={properties[propName]}
                    required={requiredFields.includes(propName)}
                    depth={depth}
                />
            ))}
            {(schema?.oneOf || schema?.anyOf || schema?.allOf) && propertyNames.length === 0 && (
                <CombinatorContent schema={schema} depth={depth} />
            )}
        </div>
    );
};
