import { FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import "./PromptTemplateEditor.css";
import { Label, LabelGroup } from "@patternfly/react-core";
import Editor from "@monaco-editor/react";
import { extractVariables } from "./extractVariables";

export type PromptTemplateEditorProps = {
    /** Initial template content to display in the editor */
    value: string;
    /** Called when the template content changes */
    onChange?: (value: string) => void;
    /** Called when extracted variables change */
    onVariablesChange?: (variables: string[]) => void;
    className?: string;
};

/**
 * Monaco-based editor for prompt template strings with live variable extraction.
 * Debounces variable extraction to avoid performance issues on rapid typing.
 */
export const PromptTemplateEditor: FunctionComponent<PromptTemplateEditorProps> = (props: PromptTemplateEditorProps) => {
    const { value, onChange, onVariablesChange, className } = props;
    const [variables, setVariables] = useState<string[]>([]);
    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Sync variable extraction for local editor display when value prop changes
    // (e.g. initial mount or after async artifact load in the parent page).
    // Note: onVariablesChange is intentionally NOT called here to avoid racing
    // with the 300ms debounced editor typing handler.
    useEffect(() => {
        const vars = extractVariables(value);
        setVariables(vars);
    }, [value]);

    const handleEditorChange = useCallback((newValue: string | undefined) => {
        const text = newValue ?? "";
        onChange?.(text);

        // Debounce variable extraction (300ms)
        if (debounceTimerRef.current !== null) {
            clearTimeout(debounceTimerRef.current);
        }
        debounceTimerRef.current = setTimeout(() => {
            const vars = extractVariables(text);
            setVariables(vars);
            onVariablesChange?.(vars);
        }, 300);
    }, [onChange, onVariablesChange]);

    // Cleanup debounce timer on unmount
    useEffect(() => {
        return () => {
            if (debounceTimerRef.current !== null) {
                clearTimeout(debounceTimerRef.current);
            }
        };
    }, []);

    const variableLabels = useMemo(() => {
        if (variables.length === 0) {
            return <span className="no-variables-text">No variables detected</span>;
        }
        return (
            <LabelGroup>
                {variables.map((varName) => (
                    <Label key={varName} color="blue" isCompact>
                        {"{{" + varName + "}}"}
                    </Label>
                ))}
            </LabelGroup>
        );
    }, [variables]);

    return (
        <div className={`prompt-template-editor ${className || ""}`}>
            <div className="editor-wrapper">
                <Editor
                    language="handlebars"
                    value={value}
                    onChange={handleEditorChange}
                    options={{
                        automaticLayout: true,
                        wordWrap: "on",
                        minimap: { enabled: false },
                        fontSize: 14,
                        lineNumbers: "on",
                        scrollBeyondLastLine: false,
                        renderWhitespace: "selection",
                        padding: { top: 8, bottom: 8 },
                    }}
                />
            </div>
            <div className="extracted-variables">
                <div className="extracted-variables-title">Extracted Variables</div>
                {variableLabels}
            </div>
        </div>
    );
};
