import { FunctionComponent, useState } from "react";
import "./DiffView.css";
import { ToggleGroup, ToggleGroupItem } from "@patternfly/react-core";
import { editor } from "monaco-editor";
import { DiffEditor } from "@monaco-editor/react";
import { ArrowsAltHIcon } from "@patternfly/react-icons";
import IDiffEditorConstructionOptions = editor.IDiffEditorConstructionOptions;

/**
 * Properties
 */
export type DiffViewProps = {
    original: string;
    originalLabel: string;
    modified: string;
    modifiedLabel: string;
    originalAriaLabel?: string;
    modifiedAriaLabel?: string;
};

/**
 * A reusable component for displaying side-by-side or inline diffs using Monaco DiffEditor.
 */
export const DiffView: FunctionComponent<DiffViewProps> = ({
    original,
    originalLabel,
    modified,
    modifiedLabel,
    originalAriaLabel = "Original",
    modifiedAriaLabel = "Modified"
}: DiffViewProps) => {
    const [diffEditorContentOptions, setDiffEditorContentOptions] = useState({
        renderSideBySide: true,
        automaticLayout: true,
        wordWrap: "off",
        readOnly: true,
        inDiffEditor: true,
        originalAriaLabel: originalAriaLabel,
        modifiedAriaLabel: modifiedAriaLabel
    } as IDiffEditorConstructionOptions);

    const [isDiffInline, setIsDiffInline] = useState(false);
    const [isDiffWrapped, setIsDiffWrapped] = useState(false);

    const switchInlineCompare = () => {
        setDiffEditorContentOptions({
            ...diffEditorContentOptions as IDiffEditorConstructionOptions,
            renderSideBySide: !diffEditorContentOptions.renderSideBySide
        });
        setIsDiffInline(!!diffEditorContentOptions.renderSideBySide);
    };

    const switchWordWrap = () => {
        setDiffEditorContentOptions({
            ...diffEditorContentOptions as IDiffEditorConstructionOptions,
            wordWrap: diffEditorContentOptions.wordWrap == "off" ? "on" : "off"
        });
        setIsDiffWrapped(diffEditorContentOptions.wordWrap != "on");
    };

    return (
        <div className="diff-view">
            <ToggleGroup className="diff-toggle-group" aria-label="Diff view toggle group">
                <ToggleGroupItem
                    text="Inline"
                    key={1}
                    buttonId="inline-toggle"
                    isSelected={isDiffInline}
                    onChange={switchInlineCompare}
                />
                <ToggleGroupItem
                    text="Wrap text"
                    key={0}
                    buttonId="wrap-toggle"
                    isSelected={isDiffWrapped}
                    onChange={switchWordWrap}
                />
            </ToggleGroup>
            <div className="diff-label">
                <span className="before">{originalLabel}</span>
                <span className="divider">
                    <ArrowsAltHIcon />
                </span>
                <span className="after">{modifiedLabel}</span>
            </div>
            <div className="diff-editor">
                <DiffEditor
                    className="text-editor"
                    original={original}
                    modified={modified}
                    options={diffEditorContentOptions}
                />
            </div>
        </div>
    );
};
