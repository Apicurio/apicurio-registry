import { describe, expect, it } from "vitest";
import {
    detectPromptVariables,
    syncPromptTemplateVariables
} from "./promptTemplateSync";

const YAML_SAMPLE = `# Prompt template configuration
templateId: greeting-service
# Template definition
template: "Hello {{name}}, your order {{orderId}} is ready."
# Declared variables
variables:
  # Customer name
  name:
    type: string
    description: "Customer name"
    default: "exampleUser"
  # Legacy unused variable
  legacyCode:
    type: integer
    description: "Legacy code"
`;

const YAML_BLOCK_SCALAR = `templateId: multiline-test
template: |
  You are an assistant.
  Review code for {{language}}:
  {{code}}
variables:
  language:
    type: string
    enum:
      - java
      - python
`;

const YAML_ARRAY_VARS = `templateId: array-test
template: "Hello {{name}} and {{extra}}"
variables:
  - name: name
    type: string
`;

const JSON_SAMPLE = `{
  "templateId": "json-test",
  "template": "Hello {{name}} and {{role}}!",
  "variables": {
    "name": {
      "type": "string",
      "description": "User name"
    },
    "unused": {
      "type": "boolean"
    }
  }
}`;

const MALFORMED_YAML = `templateId: broken
template: "Hello {{name
  broken: [
`;

const MALFORMED_JSON = `{
  "templateId": "broken-json",
  "template": "Hello {{name}}",
  "variables": {
`;

const YAML_COMPLEX_METADATA = `templateId: complex
template: "Run {{task}} with priority {{priority}}."
variables:
  priority:
    type: integer
    minimum: 1
    maximum: 10
    default: 5
    customTag: "important"
    $ref: "#/components/schemas/Priority"
`;

const YAML_FOLDED_SCALAR = `templateId: folded-test
template: >
  This is folded prompt text
  with {{author}} and {{reviewer}}.
variables:
  author:
    type: string
`;

const YAML_DUPLICATE_KEYS = `templateId: duplicates
template: "Hi {{name}}"
variables:
  other:
    type: string
  other:
    type: integer
`;

const LIST_ERROR = "'variables' is a list. Synchronization only adds entries to a mapping of variable names to schemas.";
const NOT_A_MAPPING_ERROR = "'variables' must be a mapping of variable names to schemas.";
const STUB_LINES = "    type: string\n    description: \"\"";

/** The insertion must rebuild updatedSource from the source, so the editor can apply it on its own. */
const applyInsertion = (source: string, insertion: { offset: number; text: string } | undefined): string => {
    expect(insertion).toBeDefined();
    return source.slice(0, insertion!.offset) + insertion!.text + source.slice(insertion!.offset);
};

describe("promptTemplateSync — Live Variable Detection (#10196)", () => {
    it("detects synchronized, missing, and unused variables in YAML", () => {
        const detection = detectPromptVariables(YAML_SAMPLE);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(true);
        expect(detection.templateText).toBe("Hello {{name}}, your order {{orderId}} is ready.");
        expect(detection.synchronizedCount).toBe(1); // 'name'
        expect(detection.missingCount).toBe(1); // 'orderId'
        expect(detection.unusedCount).toBe(1); // 'legacyCode'

        const missing = detection.variables.filter(v => v.source === "detected");
        expect(missing.map(v => v.name)).toEqual(["orderId"]);

        const unused = detection.variables.filter(v => v.source === "declared");
        expect(unused.map(v => v.name)).toEqual(["legacyCode"]);

        const synced = detection.variables.filter(v => v.source === "both");
        expect(synced.map(v => v.name)).toEqual(["name"]);
    });

    it("deduplicates repeated variable references in template", () => {
        const source = `templateId: dedup
template: "{{user}} asks {{question}} to {{user}}."
variables: {}
`;
        const detection = detectPromptVariables(source);
        expect(detection.variables.map(v => v.name)).toEqual(["user", "question"]);
        expect(detection.missingCount).toBe(2);
        expect(detection.synchronizedCount).toBe(0);
    });

    it("detects variables in block scalar templates", () => {
        const detection = detectPromptVariables(YAML_BLOCK_SCALAR);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(true);
        expect(detection.synchronizedCount).toBe(1); // 'language'
        expect(detection.missingCount).toBe(1); // 'code'
        expect(detection.unusedCount).toBe(0);
    });

    it("supports array-shaped variables in YAML", () => {
        const detection = detectPromptVariables(YAML_ARRAY_VARS);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(true);
        expect(detection.synchronizedCount).toBe(1); // 'name'
        expect(detection.missingCount).toBe(1); // 'extra'
    });

    it("detects variables in JSON format", () => {
        const detection = detectPromptVariables(JSON_SAMPLE);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(true);
        expect(detection.synchronizedCount).toBe(1); // 'name'
        expect(detection.missingCount).toBe(1); // 'role'
        expect(detection.unusedCount).toBe(1); // 'unused'
    });

    it("excludes Handlebars conditional keywords and helpers from detected variables", () => {
        const source = `templateId: conditionals
template: "{{#if condition}}Hello {{name}}{{else}}Goodbye {{name}}{{/if}} {{this}}"
variables:
  name:
    type: string
`;
        const detection = detectPromptVariables(source);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(true);
        // if, else, this are excluded; condition is a conditional input, not a missing placeholder
        expect(detection.variables.map(v => v.name)).toEqual(["condition", "name"]);
        expect(detection.missingCount).toBe(0);
        expect(detection.synchronizedCount).toBe(1); // 'name'
        expect(detection.conditionalCount).toBe(1); // 'condition'
    });

    it("shows condition-only subjects separately and keeps their declarations out of 'unused'", () => {
        const source = `templateId: conditions
template: "{{#if urgent}}Escalate.{{/if}}{{#unless premium}}Upgrade.{{/unless}} Hi {{name}}"
variables:
  urgent:
    type: boolean
`;
        const detection = detectPromptVariables(source);
        expect(detection.variables.map(v => [v.name, v.source, v.condition, v.placeholder])).toEqual([
            ["urgent", "both", true, false],
            ["premium", "detected", true, false],
            ["name", "detected", false, true]
        ]);
        expect(detection.conditionalCount).toBe(2);
        expect(detection.missingCount).toBe(1); // 'name'
        expect(detection.synchronizedCount).toBe(0);
        expect(detection.unusedCount).toBe(0);
    });

    it("counts a condition subject that is also a placeholder as a normal variable", () => {
        const source = `templateId: both
template: "{{#if urgent}}Urgent: {{urgent}}{{/if}}"
`;
        const detection = detectPromptVariables(source);
        expect(detection.variables.map(v => [v.name, v.condition, v.placeholder])).toEqual([["urgent", true, true]]);
        expect(detection.missingCount).toBe(1);
        expect(detection.conditionalCount).toBe(0);
    });

    it("detects bare {{lookup}} and {{log}} as variables, as the backend does", () => {
        const source = `templateId: helpers
template: "{{lookup}} {{ log }} {{lookup items 0}}"
variables:
  log:
    type: string
`;
        const detection = detectPromptVariables(source);
        expect(detection.variables.map(v => [v.name, v.source])).toEqual([["lookup", "detected"], ["log", "both"]]);
        expect(detection.missingCount).toBe(1);
        expect(detection.synchronizedCount).toBe(1);
    });

    it("reports duplicate YAML keys with their position", () => {
        const detection = detectPromptVariables(YAML_DUPLICATE_KEYS);
        expect(detection.valid).toBe(false);
        expect(detection.error).toBe("Duplicate key at line 6, column 3. Remove the duplicate before synchronizing.");
    });

    it("handles documents without a template property safely", () => {
        const noTemplate = `templateId: test
name: Test Without Template
variables:
  x:
    type: string
`;
        const detection = detectPromptVariables(noTemplate);
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(false);
        expect(detection.variables).toEqual([]);
        expect(detection.missingCount).toBe(0);
    });

    it("reports syntax error without throwing on malformed YAML", () => {
        const detection = detectPromptVariables(MALFORMED_YAML);
        expect(detection.valid).toBe(false);
        expect(detection.error).toBeDefined();
        expect(detection.variables).toEqual([]);
    });

    it("reports syntax error without throwing on malformed JSON", () => {
        const detection = detectPromptVariables(MALFORMED_JSON);
        expect(detection.valid).toBe(false);
        expect(detection.error).toBeDefined();
        expect(detection.variables).toEqual([]);
    });

    it("reports error when YAML root is not a mapping", () => {
        const scalarYaml = "- item1\n- item2";
        const detection = detectPromptVariables(scalarYaml);
        expect(detection.valid).toBe(false);
        expect(detection.error).toBe("YAML root must be a mapping.");
    });

    it("reports error when JSON root is not an object", () => {
        const arrayJson = "[\"item1\", \"item2\"]";
        const detection = detectPromptVariables(arrayJson);
        expect(detection.valid).toBe(false);
        expect(detection.error).toBe("JSON root must be an object.");
    });

    it("handles empty or whitespace-only source safely", () => {
        const detection = detectPromptVariables("   \n  ");
        expect(detection.valid).toBe(true);
        expect(detection.templateFound).toBe(false);
        expect(detection.variables).toEqual([]);
    });
});

describe("promptTemplateSync — Non-Destructive Schema Synchronization (#10196)", () => {
    it("adds missing variables as string entries with empty description in YAML", () => {
        const result = syncPromptTemplateVariables(YAML_SAMPLE);
        const stub = "  orderId:\n    type: string\n    description: \"\"\n";
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(YAML_SAMPLE + stub);
        expect(result.insertion).toEqual({ offset: YAML_SAMPLE.length, text: stub });
    });

    it("preserves existing variable metadata and comments in YAML", () => {
        const result = syncPromptTemplateVariables(YAML_SAMPLE);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toContain("# Prompt template configuration");
        expect(result.updatedSource).toContain("# Template definition");
        expect(result.updatedSource).toContain("# Declared variables");
        expect(result.updatedSource).toContain("# Customer name");
        expect(result.updatedSource).toContain("default: \"exampleUser\"");
        expect(result.updatedSource).toContain("description: \"Customer name\"");
    });

    it("does not remove unused variable declarations", () => {
        const result = syncPromptTemplateVariables(YAML_SAMPLE);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toContain("legacyCode:");
        expect(result.updatedSource).toContain("type: integer");
        expect(result.updatedSource).toContain("description: \"Legacy code\"");
    });

    it("preserves YAML multiline/block scalar styles (| and >)", () => {
        const result1 = syncPromptTemplateVariables(YAML_BLOCK_SCALAR);
        expect(result1.success).toBe(true);
        expect(result1.addedCount).toBe(1); // 'code'
        expect(result1.updatedSource).toBe(YAML_BLOCK_SCALAR + "  code:\n    type: string\n    description: \"\"\n");

        const result2 = syncPromptTemplateVariables(YAML_FOLDED_SCALAR);
        expect(result2.success).toBe(true);
        expect(result2.addedCount).toBe(1); // 'reviewer'
        expect(result2.updatedSource).toBe(YAML_FOLDED_SCALAR + "  reviewer:\n    type: string\n    description: \"\"\n");
    });

    it("preserves complex metadata, constraints, enums, $ref, and custom unknown fields", () => {
        const result = syncPromptTemplateVariables(YAML_COMPLEX_METADATA);
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1); // 'task'
        expect(result.updatedSource).toBe(YAML_COMPLEX_METADATA + "  task:\n    type: string\n    description: \"\"\n");
    });

    it("creates variables mapping if variables section does not exist in YAML", () => {
        const noVars = "templateId: minimal\ntemplate: \"Hello {{recipient}}\"\n";
        const result = syncPromptTemplateVariables(noVars);
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(noVars + "variables:\n  recipient:\n    type: string\n    description: \"\"\n");
    });

    it("refuses array-shaped variables in YAML and leaves the source untouched", () => {
        const result = syncPromptTemplateVariables(YAML_ARRAY_VARS);
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBe(LIST_ERROR);
        expect(result.updatedSource).toBe(YAML_ARRAY_VARS);
        expect(result.insertion).toBeUndefined();
    });

    it("synchronizes JSON documents while preserving formatting and existing metadata", () => {
        const result = syncPromptTemplateVariables(JSON_SAMPLE);
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(`{
  "templateId": "json-test",
  "template": "Hello {{name}} and {{role}}!",
  "variables": {
    "name": {
      "type": "string",
      "description": "User name"
    },
    "unused": {
      "type": "boolean"
    },
    "role": {
      "type": "string",
      "description": ""
    }
  }
}`);
    });

    it("refuses array-shaped variables in JSON and leaves the source untouched", () => {
        const jsonArrayVars = `{
  "templateId": "json-array",
  "template": "Hello {{name}} and {{extra}}!",
  "variables": [
    { "name": "name", "type": "string" }
  ]
}`;
        const result = syncPromptTemplateVariables(jsonArrayVars);
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBe(LIST_ERROR);
        expect(result.updatedSource).toBe(jsonArrayVars);
    });

    it("does not modify source and returns addedCount=0 when already synchronized", () => {
        const synced = "templateId: synced\ntemplate: \"Hello {{name}}\"\nvariables:\n  name:\n    type: string\n";
        const result = syncPromptTemplateVariables(synced);
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(0);
        expect(result.updatedSource).toBe(synced);
        expect(result.insertion).toBeUndefined();
    });

    it("refuses to modify malformed YAML and returns descriptive error", () => {
        const result = syncPromptTemplateVariables(MALFORMED_YAML);
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBeDefined();
        expect(result.updatedSource).toBe(MALFORMED_YAML);
    });

    it("refuses to modify malformed JSON and returns descriptive error", () => {
        const result = syncPromptTemplateVariables(MALFORMED_JSON);
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBeDefined();
        expect(result.updatedSource).toBe(MALFORMED_JSON);
    });

    it("handles empty or whitespace-only source safely", () => {
        const result = syncPromptTemplateVariables("   \n\n  ");
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBe("Document is empty.");
        expect(result.updatedSource).toBe("   \n\n  ");
    });

    it("returns error when document has no template field", () => {
        const noTemplate = "templateId: test\nname: No Template\nvariables: {}\n";
        const result = syncPromptTemplateVariables(noTemplate);
        expect(result.success).toBe(false);
        expect(result.addedCount).toBe(0);
        expect(result.error).toBe("No 'template' field found in document.");
    });
});

describe("promptTemplateSync — Targeted Insertion (#10196)", () => {
    it("inserts only the new entries, leaving comments, quoting, blank lines, flow collections and unrelated anchors exactly as written", () => {
        const source = `# Support prompt
templateId: support-reply
defaults: &defaults
  tone: 'friendly'   # single-quoted on purpose
reply: *defaults

template: |
  Hi {{customer}}, about {{topic}}:
  {{#if urgent}}We are on it.{{/if}}
variables:
  customer:
    type: string   # shown in the UI
  urgent:
    type: boolean
    default: false
metadata:
  tags: [support, email]
`;
        const result = syncPromptTemplateVariables(source);
        const stub = `  topic:\n${STUB_LINES}\n`;
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1);
        expect(result.insertion).toEqual({ offset: source.indexOf("metadata:"), text: stub });
        expect(result.updatedSource).toBe(source.replace("metadata:", `${stub}metadata:`));
        expect(applyInsertion(source, result.insertion)).toBe(result.updatedSource);
    });

    it("fills an empty 'variables:' key on the lines below it, keeping its comment", () => {
        const source = `templateId: t
template: "Hi {{name}}"
variables: # filled in later
metadata:
  owner: team
`;
        const result = syncPromptTemplateVariables(source);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toBe(source.replace(
            "variables: # filled in later\n",
            `variables: # filled in later\n  name:\n${STUB_LINES}\n`
        ));
        expect(applyInsertion(source, result.insertion)).toBe(result.updatedSource);
    });

    it("inserts into empty and non-empty YAML flow mappings in flow style", () => {
        const empty = "templateId: t\ntemplate: \"Hi {{name}} {{role}}\"\nvariables: {}\n";
        expect(syncPromptTemplateVariables(empty).updatedSource).toBe(
            "templateId: t\ntemplate: \"Hi {{name}} {{role}}\"\n"
            + "variables: {name: {type: string, description: \"\"}, role: {type: string, description: \"\"}}\n"
        );

        const filled = "templateId: t\ntemplate: \"Hi {{name}} {{role}}\"\nvariables: {name: {type: string}}\n";
        const result = syncPromptTemplateVariables(filled);
        expect(result.updatedSource).toBe(
            "templateId: t\ntemplate: \"Hi {{name}} {{role}}\"\n"
            + "variables: {name: {type: string}, role: {type: string, description: \"\"}}\n"
        );
        expect(applyInsertion(filled, result.insertion)).toBe(result.updatedSource);
    });

    it("keeps line-per-member JSON layout when filling an empty variables object", () => {
        const source = `{
  "templateId": "t",
  "template": "Hi {{name}}",
  "variables": {}
}`;
        const result = syncPromptTemplateVariables(source);
        expect(result.updatedSource).toBe(`{
  "templateId": "t",
  "template": "Hi {{name}}",
  "variables": {
    "name": {
      "type": "string",
      "description": ""
    }
  }
}`);
        expect(applyInsertion(source, result.insertion)).toBe(result.updatedSource);
    });

    it("keeps an inline JSON variables object inline and leaves number literals as written", () => {
        const source = `{
  "template": "Hi {{name}} {{role}}",
  "variables": {"name": {"type": "string", "maxLength": 1.0e3}},
  "temperature": 0.50
}`;
        const result = syncPromptTemplateVariables(source);
        expect(result.updatedSource).toBe(source.replace(
            "\"maxLength\": 1.0e3}}",
            "\"maxLength\": 1.0e3}, \"role\": {\"type\": \"string\", \"description\": \"\"}}"
        ));
    });

    it("adds a JSON variables member after the last member, multi-line or inline to match the document", () => {
        const pretty = `{
  "templateId": "t",
  "template": "Hi {{name}}"
}`;
        expect(syncPromptTemplateVariables(pretty).updatedSource).toBe(`{
  "templateId": "t",
  "template": "Hi {{name}}",
  "variables": {
    "name": {
      "type": "string",
      "description": ""
    }
  }
}`);

        const compact = "{\"template\": \"Hi {{name}}\"}";
        expect(syncPromptTemplateVariables(compact).updatedSource).toBe(
            "{\"template\": \"Hi {{name}}\", \"variables\": {\"name\": {\"type\": \"string\", \"description\": \"\"}}}"
        );
    });

    it("keeps CRLF line endings", () => {
        const source = "templateId: t\r\ntemplate: \"Hi {{name}}\"\r\nvariables:\r\n  other:\r\n    type: string\r\n";
        const result = syncPromptTemplateVariables(source);
        expect(result.updatedSource).toBe(source + "  name:\r\n    type: string\r\n    description: \"\"\r\n");
    });

    it("quotes YAML keys that would not read back as the variable name", () => {
        const source = "templateId: t\ntemplate: \"{{true}} {{yes}} {{123}}\"\n";
        const result = syncPromptTemplateVariables(source);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toBe(source
            + `variables:\n  "true":\n${STUB_LINES}\n  "yes":\n${STUB_LINES}\n  "123":\n${STUB_LINES}\n`);
    });

    it("inserts into an anchored variables mapping that nothing aliases", () => {
        const source = "template: \"Hi {{name}}\"\nvariables: &vars\n  other:\n    type: string\n";
        const result = syncPromptTemplateVariables(source);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toBe(source + `  name:\n${STUB_LINES}\n`);
    });
});

describe("promptTemplateSync — Conditional Inputs (#10196)", () => {
    it("never creates a schema entry from a condition-only reference and leaves the source identical", () => {
        const source = `templateId: t
template: "{{#if urgent}}Escalate.{{/if}}{{#unless premium}}Upgrade.{{/unless}} Hi {{name}}"
variables:
  name:
    type: string
`;
        const result = syncPromptTemplateVariables(source);
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(0);
        expect(result.updatedSource).toBe(source);
        expect(result.insertion).toBeUndefined();
    });

    it("adds a condition subject that is also used as a placeholder", () => {
        const source = "templateId: t\ntemplate: \"{{#if urgent}}Urgent: {{urgent}}{{/if}}\"\n";
        const result = syncPromptTemplateVariables(source);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(source + `variables:\n  urgent:\n${STUB_LINES}\n`);
    });
});

describe("promptTemplateSync — Refusing Ambiguous or Unsafe Synchronization (#10196)", () => {
    it.each([
        [
            "the variables mapping is an alias",
            "shared: &shared\n  name:\n    type: string\ntemplate: \"Hi {{name}} {{role}}\"\nvariables: *shared\n",
            "'variables' is an alias (*shared). Add the missing variables where &shared is defined."
        ],
        [
            "the variables mapping uses a merge key",
            "base: &base\n  name:\n    type: string\ntemplate: \"Hi {{role}}\"\nvariables:\n  <<: *base\n  other:\n    type: string\n",
            "'variables' uses a merge key (<<). Synchronization cannot tell which mapping should receive new entries."
        ],
        [
            "the variables mapping is aliased elsewhere",
            "template: \"Hi {{name}}\"\nvariables: &vars\n  other:\n    type: string\ncopy: *vars\n",
            "'variables' is shared through the anchor &vars. Adding entries would also change every alias of it."
        ],
        [
            "YAML keys are duplicated",
            YAML_DUPLICATE_KEYS,
            "Duplicate key at line 6, column 3. Remove the duplicate before synchronizing."
        ],
        [
            "JSON keys are duplicated",
            "{\n  \"template\": \"Hi {{name}}\",\n  \"variables\": {\n    \"other\": {},\n    \"other\": {}\n  }\n}",
            "Duplicate key at line 5, column 5. Remove the duplicate before synchronizing."
        ],
        ["variables is a YAML scalar", "template: \"Hi {{name}}\"\nvariables: none\n", NOT_A_MAPPING_ERROR],
        ["variables is an explicit YAML null", "template: \"Hi {{name}}\"\nvariables: null\n", NOT_A_MAPPING_ERROR],
        ["variables is JSON null", "{\"template\": \"Hi {{name}}\", \"variables\": null}", NOT_A_MAPPING_ERROR],
        [
            "the YAML root is a flow mapping without variables",
            "--- {template: \"Hi {{name}}\"}\n",
            "The document root is a flow mapping. Add a 'variables' mapping before synchronizing."
        ],
        [
            "the insertion would not read back as exactly the new entries",
            "--- {template: \"Hi {{name}}\", variables: }\n",
            "Could not add the missing variables without changing other content. Add them manually."
        ]
    ])("refuses when %s and leaves the source untouched", (_case, source, error) => {
        const result = syncPromptTemplateVariables(source);
        expect(result.success).toBe(false);
        expect(result.error).toBe(error);
        expect(result.addedCount).toBe(0);
        expect(result.updatedSource).toBe(source);
        expect(result.insertion).toBeUndefined();
    });
});

describe("promptTemplateSync — Renamed and Removed Variables (#10196)", () => {
    it("adds the new name and keeps the old declarations when a variable is renamed or removed in the template", () => {
        const source = `templateId: renamed
template: "Hello {{fullName}}"
variables:
  name:
    type: string
    description: "Customer name"
    default: "exampleUser"
  legacyCode:
    type: integer
`;
        const detection = detectPromptVariables(source);
        expect(detection.variables.map(v => [v.name, v.source])).toEqual([
            ["fullName", "detected"],
            ["name", "declared"],
            ["legacyCode", "declared"]
        ]);
        expect(detection.missingCount).toBe(1);
        expect(detection.unusedCount).toBe(2);

        const result = syncPromptTemplateVariables(source);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(source + `  fullName:\n${STUB_LINES}\n`);
    });
});

describe("promptTemplateSync — Unreadable Variables (#10196)", () => {
    const UNRESOLVED_ALIAS_ERROR = "The alias *missing has no anchor &missing before it. Define the anchor or remove the alias.";
    const EMPTY_ENTRY_ERROR = "'variables' entry 1 is empty. Each entry of a 'variables' list must be a mapping with a 'name'.";

    it.each([
        ["an alias without an anchor", "template: \"Hi {{name}}\"\nvariables: *missing\n", "application/x-yaml", UNRESOLVED_ALIAS_ERROR],
        ["a YAML list containing null", "template: \"Hi {{name}}\"\nvariables: [null]\n", "application/x-yaml", EMPTY_ENTRY_ERROR],
        ["a JSON list containing null", "{\"template\": \"Hi {{name}}\", \"variables\": [null]}", "application/json", EMPTY_ENTRY_ERROR],
        [
            "a list entry that is not a mapping",
            "template: \"Hi {{name}}\"\nvariables:\n  - name: name\n  - role\n",
            "application/x-yaml",
            "'variables' entry 2 is not a mapping. Each entry of a 'variables' list must be a mapping with a 'name'."
        ]
    ])("reports %s as an error instead of throwing, and leaves the source untouched", (_case, source, contentType, error) => {
        expect(detectPromptVariables(source, contentType)).toEqual({
            valid: false,
            error,
            templateFound: false,
            variables: [],
            missingCount: 0,
            unusedCount: 0,
            synchronizedCount: 0,
            conditionalCount: 0
        });
        expect(syncPromptTemplateVariables(source, contentType)).toEqual({
            success: false,
            updatedSource: source,
            addedCount: 0,
            error
        });
    });

    it("detects variables again once the missing anchor is defined", () => {
        const corrected = "defaults: &missing\n  name:\n    type: string\ntemplate: \"Hi {{name}} {{role}}\"\nvariables: *missing\n";
        const detection = detectPromptVariables(corrected, "application/x-yaml");
        expect(detection.valid).toBe(true);
        expect(detection.variables.map(v => [v.name, v.source])).toEqual([["name", "both"], ["role", "detected"]]);
        expect(detection.synchronizedCount).toBe(1);
        expect(detection.missingCount).toBe(1);
    });

    it("detects and synchronizes again once the empty list entry is replaced by a mapping", () => {
        const corrected = "template: \"Hi {{name}} {{role}}\"\nvariables:\n  name:\n    type: string\n";
        const detection = detectPromptVariables(corrected, "application/x-yaml");
        expect(detection.valid).toBe(true);
        expect(detection.synchronizedCount).toBe(1);
        expect(detection.missingCount).toBe(1);

        const result = syncPromptTemplateVariables(corrected, "application/x-yaml");
        expect(result.success).toBe(true);
        expect(result.updatedSource).toBe(corrected + `  role:\n${STUB_LINES}\n`);
    });
});

describe("promptTemplateSync — Content Type (#10196)", () => {
    const FLOW_YAML = "{templateId: t, template: \"Hi {{name}} {{role}}\", variables: {name: {type: string}}}";

    it("reads flow-style YAML as YAML when the content type is YAML, and as invalid JSON when it is JSON", () => {
        const detection = detectPromptVariables(FLOW_YAML, "application/x-yaml");
        expect(detection.valid).toBe(true);
        expect(detection.variables.map(v => [v.name, v.source])).toEqual([["name", "both"], ["role", "detected"]]);
        expect(detection.synchronizedCount).toBe(1);
        expect(detection.missingCount).toBe(1);

        expect(detectPromptVariables(FLOW_YAML, "application/json").valid).toBe(false);
    });

    it("synchronizes flow-style YAML in flow style", () => {
        const result = syncPromptTemplateVariables(FLOW_YAML, "application/x-yaml");
        expect(result.success).toBe(true);
        expect(result.addedCount).toBe(1);
        expect(result.updatedSource).toBe(
            "{templateId: t, template: \"Hi {{name}} {{role}}\", variables: {name: {type: string}, role: {type: string, description: \"\"}}}"
        );
        expect(applyInsertion(FLOW_YAML, result.insertion)).toBe(result.updatedSource);
    });

    it("explains that a flow-style YAML root needs a variables mapping instead of reporting a JSON error", () => {
        const source = "{templateId: t, template: \"Hi {{name}}\"}";
        const result = syncPromptTemplateVariables(source, "application/x-yaml");
        expect(result.success).toBe(false);
        expect(result.error).toBe("The document root is a flow mapping. Add a 'variables' mapping before synchronizing.");
        expect(result.updatedSource).toBe(source);
    });

    it.each(["application/yaml", "application/yaml; charset=utf-8"])("reads and synchronizes flow-style YAML for %s too", (contentType) => {
        const detection = detectPromptVariables(FLOW_YAML, contentType);
        expect(detection.valid).toBe(true);
        expect(detection.synchronizedCount).toBe(1);
        expect(detection.missingCount).toBe(1);

        const result = syncPromptTemplateVariables(FLOW_YAML, contentType);
        expect(result.success).toBe(true);
        expect(result.updatedSource).toBe(
            "{templateId: t, template: \"Hi {{name}} {{role}}\", variables: {name: {type: string}, role: {type: string, description: \"\"}}}"
        );
    });

    it("writes entries in the format the content type names, not the one the first character suggests", () => {
        const source = "{\"template\": \"Hi {{name}}\", \"variables\": {}}";
        expect(syncPromptTemplateVariables(source, "application/json; charset=utf-8").updatedSource).toBe(
            "{\"template\": \"Hi {{name}}\", \"variables\": {\"name\": {\"type\": \"string\", \"description\": \"\"}}}"
        );
        expect(syncPromptTemplateVariables(source, "application/x-yaml").updatedSource).toBe(
            "{\"template\": \"Hi {{name}}\", \"variables\": {name: {type: string, description: \"\"}}}"
        );
    });
});

describe("promptTemplateSync — Recursive Aliases (#10196)", () => {
    const RECURSIVE_METADATA = `templateId: t
template: "Hi {{name}} {{role}}"
variables:
  name:
    type: string
metadata: &meta
  owner: team
  self: *meta
`;

    it("still detects variables when unrelated metadata is recursive", () => {
        const detection = detectPromptVariables(RECURSIVE_METADATA, "application/x-yaml");
        expect(detection.valid).toBe(true);
        expect(detection.synchronizedCount).toBe(1);
        expect(detection.missingCount).toBe(1);
    });

    it("refuses to synchronize a recursive document instead of throwing, and leaves the source untouched", () => {
        expect(syncPromptTemplateVariables(RECURSIVE_METADATA, "application/x-yaml")).toEqual({
            success: false,
            updatedSource: RECURSIVE_METADATA,
            addedCount: 0,
            error: "The alias *meta is inside the node it refers to, so the document is recursive. "
                + "Synchronization does not support recursive structures. Add the missing variables manually."
        });
    });

    it("leaves a recursive document unchanged without an error when nothing is missing", () => {
        const synchronized = RECURSIVE_METADATA.replace(" {{role}}", "");
        expect(syncPromptTemplateVariables(synchronized, "application/x-yaml")).toEqual({
            success: true,
            updatedSource: synchronized,
            addedCount: 0
        });
    });
});
