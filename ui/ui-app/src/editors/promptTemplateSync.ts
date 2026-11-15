import YAML, { type Document, type Pair, type YAMLError, type YAMLMap } from "yaml";
import {
    extractTemplateVariableReferences,
    reconcileTemplateVariables,
    ReconciledVariable
} from "@app/components/promptTemplate/promptTemplateVariables";
import { toDeclaredMap } from "@app/components/promptTemplate/PromptTemplateTestPanel.utils";

/** A reconciled variable plus how the template uses it. */
export type DetectedVariable = ReconciledVariable & {
    /** Substituted as a placeholder, e.g. `{{name}}`. */
    placeholder: boolean;
    /** Tested by `{{#if name}}` or `{{#unless name}}`. */
    condition: boolean;
};

export interface VariableDetectionResult {
    valid: boolean;
    error?: string;
    templateFound: boolean;
    templateText?: string;
    variables: DetectedVariable[];
    /** Placeholders without a declaration: what Sync Variables adds. */
    missingCount: number;
    /** Declarations the template never references. */
    unusedCount: number;
    /** Placeholders that have a declaration. */
    synchronizedCount: number;
    /** Names the template only tests in `{{#if}}` / `{{#unless}}`; Sync Variables never adds these. */
    conditionalCount: number;
}

/** Text inserted at one offset of the source. */
export interface TextInsertion {
    offset: number;
    text: string;
}

export interface VariableSyncResult {
    success: boolean;
    updatedSource: string;
    addedCount: number;
    /** The one insertion that turns the source into updatedSource; absent when nothing changes. */
    insertion?: TextInsertion;
    error?: string;
}

type ArtifactData = Record<string, any>;

type Refusal = { error: string };

const UNSAFE_RESULT_ERROR = "Could not add the missing variables without changing other content. Add them manually.";
const LIST_ERROR = "'variables' is a list. Synchronization only adds entries to a mapping of variable names to schemas.";
const NOT_A_MAPPING_ERROR = "'variables' must be a mapping of variable names to schemas.";
const MERGE_KEY_ERROR = "'variables' uses a merge key (<<). Synchronization cannot tell which mapping should receive new entries.";
const FLOW_ROOT_ERROR = "The document root is a flow mapping. Add a 'variables' mapping before synchronizing.";

const STUB_SCHEMA = { type: "string", description: "" };

const isJsonContent = (content: string): boolean => {
    const trimmed = content.trim();
    return trimmed.startsWith("{") || trimmed.startsWith("[");
};

const isPlainObject = (value: unknown): value is ArtifactData => {
    return value !== null && typeof value === "object" && !Array.isArray(value);
};

const describeYamlError = (error: YAMLError): string => {
    if (error.code === "DUPLICATE_KEY") {
        const position = error.linePos?.[0];
        const where = position ? ` at line ${position.line}, column ${position.col}` : "";
        return `Duplicate key${where}. Remove the duplicate before synchronizing.`;
    }
    return error.message;
};

/** Reads the artifact source as plain data. */
const parseArtifact = (source: string): { data: ArtifactData; json: boolean } | Refusal => {
    if (isJsonContent(source)) {
        let data: unknown;
        try {
            data = JSON.parse(source);
        } catch (err: any) {
            return { error: err?.message || "Invalid JSON" };
        }
        return isPlainObject(data) ? { data, json: true } : { error: "JSON root must be an object." };
    }

    const doc = YAML.parseDocument(source);
    if (doc.errors.length > 0) {
        return { error: describeYamlError(doc.errors[0]) };
    }
    const data = doc.toJSON();
    return isPlainObject(data) ? { data, json: false } : { error: "YAML root must be a mapping." };
};

/**
 * Live detection of variables from template text and reconciliation against
 * declared variables in the artifact source (YAML or JSON).
 */
export function detectPromptVariables(source: string): VariableDetectionResult {
    const emptyResult: VariableDetectionResult = {
        valid: true,
        templateFound: false,
        variables: [],
        missingCount: 0,
        unusedCount: 0,
        synchronizedCount: 0,
        conditionalCount: 0
    };

    if (!source || !source.trim()) {
        return emptyResult;
    }

    const parsed = parseArtifact(source);
    if ("error" in parsed) {
        return { ...emptyResult, valid: false, error: parsed.error };
    }

    const template = parsed.data.template;
    if (typeof template !== "string") {
        return emptyResult;
    }

    const references = extractTemplateVariableReferences(template);
    const usage = new Map(references.map(reference => [reference.name, reference]));
    const variables: DetectedVariable[] = reconcileTemplateVariables(
        references.map(reference => reference.name),
        toDeclaredMap(parsed.data.variables)
    ).map(variable => ({
        ...variable,
        placeholder: usage.get(variable.name)?.placeholder ?? false,
        condition: usage.get(variable.name)?.condition ?? false
    }));
    const count = (predicate: (variable: DetectedVariable) => boolean): number => variables.filter(predicate).length;

    return {
        valid: true,
        templateFound: true,
        templateText: template,
        variables,
        missingCount: count(variable => variable.placeholder && variable.source === "detected"),
        unusedCount: count(variable => variable.source === "declared"),
        synchronizedCount: count(variable => variable.placeholder && variable.source === "both"),
        conditionalCount: count(variable => variable.condition && !variable.placeholder)
    };
}

/**
 * Adds a `type: string` / empty `description` entry for every placeholder the
 * template uses but `variables` does not declare. The result is a single text
 * insertion, so comments, scalar styles, anchors, formatting and every existing
 * declaration stay exactly as written. Names used only as `{{#if}}` /
 * `{{#unless}}` subjects are never added. When the insertion would be ambiguous
 * or unsafe, nothing changes and the error explains why.
 */
export function syncPromptTemplateVariables(source: string): VariableSyncResult {
    const refuse = (error: string): VariableSyncResult => ({ success: false, updatedSource: source, addedCount: 0, error });

    if (!source || !source.trim()) {
        return refuse("Document is empty.");
    }

    const parsed = parseArtifact(source);
    if ("error" in parsed) {
        return refuse(parsed.error);
    }

    const { data, json } = parsed;
    if (typeof data.template !== "string") {
        return refuse("No 'template' field found in document.");
    }

    const declared = new Set(Object.keys(toDeclaredMap(data.variables) ?? {}));
    const missing = extractTemplateVariableReferences(data.template)
        .filter(reference => reference.placeholder && !declared.has(reference.name))
        .map(reference => reference.name);
    if (missing.length === 0) {
        return { success: true, updatedSource: source, addedCount: 0 };
    }

    const insertion = planInsertion(source, json, missing);
    if ("error" in insertion) {
        return refuse(insertion.error);
    }

    const updatedSource = source.slice(0, insertion.offset) + insertion.text + source.slice(insertion.offset);
    if (!addsExactly(updatedSource, data, missing)) {
        return refuse(UNSAFE_RESULT_ERROR);
    }

    return { success: true, updatedSource, addedCount: missing.length, insertion };
}

/** True when the updated source reads as the original data plus exactly the new entries. */
const addsExactly = (updatedSource: string, data: ArtifactData, names: string[]): boolean => {
    const reread = parseArtifact(updatedSource);
    if ("error" in reread) {
        return false;
    }
    const declared = isPlainObject(data.variables) ? data.variables : {};
    const expected = {
        ...data,
        variables: Object.fromEntries([...Object.entries(declared), ...names.map(name => [name, STUB_SCHEMA])])
    };
    return JSON.stringify(reread.data) === JSON.stringify(expected);
};

const planInsertion = (source: string, json: boolean, names: string[]): TextInsertion | Refusal => {
    // JSON is read with the YAML parser too: it gives node offsets and reports duplicate keys.
    const doc = YAML.parseDocument(source);
    if (doc.errors.length > 0) {
        return { error: describeYamlError(doc.errors[0]) };
    }
    const root = doc.contents;
    if (!YAML.isMap(root)) {
        return { error: UNSAFE_RESULT_ERROR };
    }

    const eol = source.includes("\r\n") ? "\r\n" : "\n";
    const pair = root.items.find(item => YAML.isScalar(item.key) && item.key.value === "variables");
    if (!pair) {
        return json ? insertJsonVariablesMember(source, root, names, eol) : insertYamlVariablesSection(source, root, names, eol);
    }

    const value = pair.value;
    if (!json) {
        const refusal = refuseSharedYaml(doc, value);
        if (refusal) {
            return { error: refusal };
        }
    }
    if (YAML.isSeq(value)) {
        return { error: LIST_ERROR };
    }
    if (!json && YAML.isScalar(value) && value.value === null && nodeStart(value) === nodeEnd(value)) {
        // `variables:` with nothing after it: start the mapping on the lines below the key.
        const keyIndent = indentAt(source, nodeStart(pair.key));
        const unit = yamlUnit(source, root);
        return { offset: lineEndAt(source, nodeEnd(pair.key)), text: eol + yamlBlockStubs(names, keyIndent + unit, unit).join(eol) };
    }
    if (!YAML.isMap(value)) {
        return { error: NOT_A_MAPPING_ERROR };
    }
    if (json) {
        return insertJsonStubs(source, root, pair, value, names, eol);
    }
    return value.flow ? insertYamlFlowStubs(value, names) : insertYamlBlockStubs(source, root, pair, value, names, eol);
};

/** Refuses a YAML `variables` value whose entries come from, or are shared with, another node. */
const refuseSharedYaml = (doc: Document, value: unknown): string | undefined => {
    if (YAML.isAlias(value)) {
        return `'variables' is an alias (*${value.source}). Add the missing variables where &${value.source} is defined.`;
    }
    if (!YAML.isMap(value)) {
        return undefined;
    }
    if (value.items.some(item => YAML.isScalar(item.key) && item.key.value === "<<")) {
        return MERGE_KEY_ERROR;
    }
    if (value.anchor && isAliased(doc, value.anchor)) {
        return `'variables' is shared through the anchor &${value.anchor}. Adding entries would also change every alias of it.`;
    }
    return undefined;
};

const isAliased = (doc: Document, anchor: string): boolean => {
    let aliased = false;
    YAML.visit(doc, {
        Alias(_key, node) {
            if (node.source === anchor) {
                aliased = true;
                return YAML.visit.BREAK;
            }
            return undefined;
        }
    });
    return aliased;
};

const insertYamlVariablesSection = (source: string, root: YAMLMap, names: string[], eol: string): TextInsertion | Refusal => {
    if (root.flow) {
        return { error: FLOW_ROOT_ERROR };
    }
    const rootIndent = indentAt(source, nodeStart(root.items[0].key));
    const unit = yamlUnit(source, root);
    const lines = [`${rootIndent}variables:`, ...yamlBlockStubs(names, rootIndent + unit, unit)];
    return linesAfter(source, nodeEnd(root), lines, eol);
};

const insertYamlBlockStubs = (
    source: string,
    root: YAMLMap,
    pair: Pair,
    variables: YAMLMap,
    names: string[],
    eol: string
): TextInsertion => {
    const entryIndent = indentAt(source, nodeStart(variables.items[0].key));
    const nested = firstNestedIndent(source, variables);
    const unit = (nested && extraIndent(nested, entryIndent))
        ?? extraIndent(entryIndent, indentAt(source, nodeStart(pair.key)))
        ?? yamlUnit(source, root);
    return linesAfter(source, nodeEnd(variables), yamlBlockStubs(names, entryIndent, unit), eol);
};

const insertYamlFlowStubs = (variables: YAMLMap, names: string[]): TextInsertion => {
    const entries = names.map(name => `${yamlKey(name)}: {type: string, description: ""}`).join(", ");
    return { offset: flowInsertionOffset(variables), text: variables.items.length > 0 ? `, ${entries}` : entries };
};

const insertJsonStubs = (
    source: string,
    root: YAMLMap,
    pair: Pair,
    variables: YAMLMap,
    names: string[],
    eol: string
): TextInsertion => {
    const offset = flowInsertionOffset(variables);
    const keyIndent = indentAt(source, nodeStart(pair.key));

    if (variables.items.length > 0) {
        const lastKeyStart = nodeStart(variables.items[variables.items.length - 1].key);
        if (!source.slice(nodeStart(variables), lastKeyStart).includes("\n")) {
            return { offset, text: `, ${jsonInlineStubs(names)}` };
        }
        const memberIndent = indentAt(source, lastKeyStart);
        const unit = extraIndent(memberIndent, keyIndent) ?? "  ";
        return { offset, text: `,${eol}${jsonBlockStubs(names, memberIndent, unit, eol)}` };
    }

    // An empty object: lay the entries out like the members around it.
    if (!source.slice(nodeStart(root), nodeStart(pair.key)).includes("\n")) {
        return { offset, text: jsonInlineStubs(names) };
    }
    const unit = extraIndent(keyIndent, indentAt(source, nodeStart(root))) ?? "  ";
    return { offset, text: `${eol}${jsonBlockStubs(names, keyIndent + unit, unit, eol)}${eol}${keyIndent}` };
};

const insertJsonVariablesMember = (source: string, root: YAMLMap, names: string[], eol: string): TextInsertion => {
    const last = root.items[root.items.length - 1];
    const offset = nodeEnd(last.value ?? last.key);
    const lastKeyStart = nodeStart(last.key);
    if (!source.slice(nodeStart(root), lastKeyStart).includes("\n")) {
        return { offset, text: `, "variables": {${jsonInlineStubs(names)}}` };
    }
    const keyIndent = indentAt(source, lastKeyStart);
    const unit = extraIndent(keyIndent, indentAt(source, nodeStart(root))) ?? "  ";
    const stubs = jsonBlockStubs(names, keyIndent + unit, unit, eol);
    return { offset, text: `,${eol}${keyIndent}"variables": {${eol}${stubs}${eol}${keyIndent}}` };
};

// YAML 1.1 parsers (such as the backend's) read these plain words as booleans or null.
const YAML_11_SCALAR_WORDS = /^(?:y|n|yes|no|true|false|on|off|null)$/i;

/** Variable names are `\w+`; quote the ones a YAML parser would not read back as that string. */
const yamlKey = (name: string): string => {
    return /^[A-Za-z_]\w*$/.test(name) && !YAML_11_SCALAR_WORDS.test(name) ? name : JSON.stringify(name);
};

const yamlBlockStubs = (names: string[], indent: string, unit: string): string[] => {
    return names.flatMap(name => [
        `${indent}${yamlKey(name)}:`,
        `${indent}${unit}type: string`,
        `${indent}${unit}description: ""`
    ]);
};

const jsonInlineStubs = (names: string[]): string => {
    return names.map(name => `${JSON.stringify(name)}: {"type": "string", "description": ""}`).join(", ");
};

const jsonBlockStubs = (names: string[], indent: string, unit: string, eol: string): string => {
    return names.map(name => [
        `${indent}${JSON.stringify(name)}: {`,
        `${indent}${unit}"type": "string",`,
        `${indent}${unit}"description": ""`,
        `${indent}}`
    ].join(eol)).join(`,${eol}`);
};

const nodeStart = (node: unknown): number => (node as { range: [number, number, number] }).range[0];

const nodeEnd = (node: unknown): number => (node as { range: [number, number, number] }).range[1];

/** Leading whitespace of the line that contains the offset. */
const indentAt = (source: string, offset: number): string => {
    const lineStart = source.lastIndexOf("\n", offset - 1) + 1;
    return /^[ \t]*/.exec(source.slice(lineStart, offset))![0];
};

/** Offset where the line that contains the offset ends, before its line break. */
const lineEndAt = (source: string, offset: number): number => {
    const newline = source.indexOf("\n", offset);
    if (newline === -1) {
        return source.length;
    }
    return source[newline - 1] === "\r" ? newline - 1 : newline;
};

/** Inserts whole lines after a block node that ends at `end`. */
const linesAfter = (source: string, end: number, lines: string[], eol: string): TextInsertion => {
    if (source[end - 1] === "\n") {
        return { offset: end, text: lines.join(eol) + eol };
    }
    return { offset: lineEndAt(source, end), text: eol + lines.join(eol) };
};

/** The part of `inner` indentation that goes deeper than `outer`, if it does. */
const extraIndent = (inner: string, outer: string): string | undefined => {
    return inner.length > outer.length && inner.startsWith(outer) ? inner.slice(outer.length) : undefined;
};

/** Indentation of the first key of the first non-empty block mapping among the map's values. */
const firstNestedIndent = (source: string, map: YAMLMap): string | undefined => {
    for (const item of map.items) {
        if (YAML.isMap(item.value) && !item.value.flow && item.value.items.length > 0) {
            return indentAt(source, nodeStart(item.value.items[0].key));
        }
    }
    return undefined;
};

/** The indentation step the document uses below its root keys, or two spaces. */
const yamlUnit = (source: string, root: YAMLMap): string => {
    const nested = firstNestedIndent(source, root);
    const rootIndent = indentAt(source, nodeStart(root.items[0].key));
    return (nested && extraIndent(nested, rootIndent)) ?? "  ";
};

/** Where a new entry goes in a flow mapping: after its last entry, or just inside `{` when empty. */
const flowInsertionOffset = (map: YAMLMap): number => {
    if (map.items.length > 0) {
        const last = map.items[map.items.length - 1];
        return nodeEnd(last.value ?? last.key);
    }
    return nodeStart(map) + 1;
};
