/**
 * Generates an example JSON value that validates against a given JSON Schema.
 * Walks the schema recursively, producing realistic placeholder values based
 * on type, format, pattern, enum, and default constraints.
 */
export const generateJsonExample = (schema: any): any => {
    if (!schema) {
        return {};
    }
    return generateValue(schema, 0);
};

const MAX_DEPTH = 8;

const generateValue = (schema: any, depth: number): any => {
    if (depth > MAX_DEPTH) {
        return undefined;
    }

    // Use default if provided
    if (schema.default !== undefined) {
        return schema.default;
    }

    // Use first example if provided
    if (schema.examples && Array.isArray(schema.examples) && schema.examples.length > 0) {
        return schema.examples[0];
    }

    // Use const if provided
    if (schema.const !== undefined) {
        return schema.const;
    }

    // Use first enum value if provided
    if (schema.enum && Array.isArray(schema.enum) && schema.enum.length > 0) {
        return schema.enum[0];
    }

    // Handle combinators: pick the first option
    if (schema.oneOf && Array.isArray(schema.oneOf) && schema.oneOf.length > 0) {
        return generateValue(schema.oneOf[0], depth);
    }
    if (schema.anyOf && Array.isArray(schema.anyOf) && schema.anyOf.length > 0) {
        return generateValue(schema.anyOf[0], depth);
    }
    if (schema.allOf && Array.isArray(schema.allOf)) {
        // Merge all allOf schemas into one object
        const merged: any = {};
        for (const sub of schema.allOf) {
            const val = generateValue(sub, depth);
            if (val && typeof val === "object" && !Array.isArray(val)) {
                Object.assign(merged, val);
            }
        }
        return Object.keys(merged).length > 0 ? merged : generateValue(schema.allOf[0], depth);
    }

    const type = Array.isArray(schema.type) ? schema.type[0] : schema.type;

    switch (type) {
        case "object":
            return generateObject(schema, depth);
        case "array":
            return generateArray(schema, depth);
        case "string":
            return generateString(schema);
        case "number":
            return generateNumber(schema);
        case "integer":
            return generateInteger(schema);
        case "boolean":
            return true;
        case "null":
            return null;
        default:
            // No type specified but has properties -> treat as object
            if (schema.properties) {
                return generateObject(schema, depth);
            }
            return "example";
    }
};

const generateObject = (schema: any, depth: number): any => {
    const result: any = {};
    const properties = schema.properties || {};
    const required: string[] = schema.required || [];

    // Generate required properties first, then optional ones
    const allKeys = Object.keys(properties);
    for (const key of allKeys) {
        const value = generateValue(properties[key], depth + 1);
        if (value !== undefined) {
            result[key] = value;
        }
    }

    // If no properties defined but additionalProperties is a schema, add one example entry
    if (allKeys.length === 0 && schema.additionalProperties
        && typeof schema.additionalProperties === "object") {
        result["key1"] = generateValue(schema.additionalProperties, depth + 1);
    }

    // Ensure required fields are present even if somehow missed
    for (const req of required) {
        if (result[req] === undefined) {
            result[req] = "example";
        }
    }

    return result;
};

const generateArray = (schema: any, depth: number): any[] => {
    const minItems = schema.minItems || 1;
    const count = Math.min(minItems, 3);
    const items = schema.items;

    if (!items) {
        return ["example"];
    }

    const result: any[] = [];
    for (let i = 0; i < count; i++) {
        const value = generateValue(items, depth + 1);
        if (value !== undefined) {
            result.push(value);
        }
    }
    return result.length > 0 ? result : ["example"];
};

const generateString = (schema: any): string => {
    // Check format first for realistic values
    if (schema.format) {
        return getFormatExample(schema.format);
    }

    // Try to generate from pattern
    if (schema.pattern) {
        return getPatternExample(schema.pattern);
    }

    // Use description as a hint for the field name
    const minLen = schema.minLength || 0;
    const placeholder = "example";
    if (minLen > placeholder.length) {
        return placeholder.padEnd(minLen, "x");
    }
    return placeholder;
};

const getFormatExample = (format: string): string => {
    switch (format) {
        case "date-time":
            return "2025-01-15T10:30:00Z";
        case "date":
            return "2025-01-15";
        case "time":
            return "10:30:00Z";
        case "email":
            return "user@example.com";
        case "uri":
        case "url":
            return "https://example.com";
        case "uri-reference":
            return "/path/to/resource";
        case "hostname":
            return "example.com";
        case "ipv4":
            return "192.168.1.1";
        case "ipv6":
            return "2001:db8::1";
        case "uuid":
            return "550e8400-e29b-41d4-a716-446655440000";
        case "duration":
            return "P1D";
        default:
            return "example";
    }
};

const getPatternExample = (pattern: string): string => {
    // Try to produce a value matching common patterns
    // Pattern: ^PREFIX-[0-9]{N}$  or ^PREFIX-[A-Z0-9]{N}$
    const prefixDigits = pattern.match(/^\^?([A-Z_-]+)-?\[(?:0-9|A-Z0-9|A-Za-z0-9)\]\{(\d+)\}\$?$/);
    if (prefixDigits) {
        const prefix = prefixDigits[1];
        const len = parseInt(prefixDigits[2], 10);
        const isAlphaNum = pattern.includes("A-Z");
        const chars = isAlphaNum ? "A0".repeat(Math.ceil(len / 2)).slice(0, len)
            : "1234567890".repeat(Math.ceil(len / 10)).slice(0, len);
        return `${prefix}-${chars}`;
    }

    // Pattern: ^[A-Z]{3}$  (e.g. currency codes)
    const fixedAlpha = pattern.match(/^\^?\[A-Z\]\{(\d+)\}\$?$/);
    if (fixedAlpha) {
        return "USD".slice(0, parseInt(fixedAlpha[1], 10)).padEnd(parseInt(fixedAlpha[1], 10), "X");
    }

    // Pattern: ^[0-9]{N,M}$  (e.g. account numbers)
    const fixedDigits = pattern.match(/^\^?\[0-9\]\{(\d+)(?:,\d+)?\}\$?$/);
    if (fixedDigits) {
        const len = parseInt(fixedDigits[1], 10);
        return "1234567890".repeat(Math.ceil(len / 10)).slice(0, len);
    }

    // Pattern: ^[a-zA-Z0-9_]{3,20}$  (e.g. usernames)
    const alphaRange = pattern.match(/^\^?\[a-zA-Z0-9[_-]*\]\{(\d+),\d+\}\$?$/);
    if (alphaRange) {
        return "example_user";
    }

    // Pattern for semver
    if (pattern.includes("\\d+\\.\\d+\\.\\d+")) {
        return "1.0.0";
    }

    // Pattern for locale codes like ^[a-z]{2}(-[A-Z]{2})?$
    if (pattern.includes("[a-z]") && pattern.includes("[A-Z]")) {
        return "en-US";
    }

    // Pattern for phone numbers
    if (pattern.includes("\\+") && pattern.includes("\\d")) {
        return "+15551234567";
    }

    return "example";
};

const generateNumber = (schema: any): number => {
    const min = schema.minimum ?? schema.exclusiveMinimum ?? 0;
    const max = schema.maximum ?? schema.exclusiveMaximum ?? undefined;

    let value: number;
    if (max !== undefined) {
        value = (min + max) / 2;
    } else {
        value = min > 0 ? min + 10.5 : 42.5;
    }

    if (schema.multipleOf) {
        value = Math.ceil(value / schema.multipleOf) * schema.multipleOf;
        // Round to avoid floating point issues
        const decimals = (schema.multipleOf.toString().split(".")[1] || "").length;
        value = parseFloat(value.toFixed(decimals));
    }

    return value;
};

const generateInteger = (schema: any): number => {
    const min = schema.minimum ?? schema.exclusiveMinimum ?? 0;
    const max = schema.maximum ?? schema.exclusiveMaximum ?? undefined;

    if (max !== undefined) {
        return Math.round((min + max) / 2);
    }
    return min > 0 ? min + 1 : 1;
};
