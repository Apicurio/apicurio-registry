import { FunctionComponent, useEffect, useRef, useState } from "react";
import "./DiffView.css";
import { ToggleGroup, ToggleGroupItem } from "@patternfly/react-core";
import type { editor } from "monaco-editor";
import type { Monaco } from "@monaco-editor/react";
import { RegistryDiffEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { ArrowsAltHIcon } from "@patternfly/react-icons";
import { ContentMarker } from "@utils/marker.utils.ts";
type IDiffEditorConstructionOptions = editor.IDiffEditorConstructionOptions;

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
    /** A fixed editor height, for containers that don't give the editor one. */
    height?: string;
    originalMarkers?: ContentMarker[];
    modifiedMarkers?: ContentMarker[];
    /** A range to scroll to in the modified side; pass a new object to scroll again. */
    revealModified?: ContentMarker;
};

const MARKER_OWNER: string = "apicurio-diff-view";

/**
 * A reusable component for displaying side-by-side or inline diffs using Monaco DiffEditor.
 */
export const DiffView: FunctionComponent<DiffViewProps> = ({
    original,
    originalLabel,
    modified,
    modifiedLabel,
    originalAriaLabel = "Original",
    modifiedAriaLabel = "Modified",
    height,
    originalMarkers,
    modifiedMarkers,
    revealModified
}: DiffViewProps) => {
    const [diffEditorContentOptions, setDiffEditorContentOptions] = useState({
        renderSideBySide: true,
        automaticLayout: true,
        wordWrap: "off",
        readOnly: true,
        // Monaco hides markers in read-only editors by default. Show them only for callers that pass
        // markers, so other diffs are unchanged.
        renderValidationDecorations: originalMarkers !== undefined || modifiedMarkers !== undefined ? "on" : "editable",
        inDiffEditor: true,
        originalAriaLabel: originalAriaLabel,
        modifiedAriaLabel: modifiedAriaLabel
    } as IDiffEditorConstructionOptions);

    const [isDiffInline, setIsDiffInline] = useState(false);
    const [isDiffWrapped, setIsDiffWrapped] = useState(false);
    const [diffEditor, setDiffEditor] = useState<editor.IStandaloneDiffEditor>();
    const monacoRef = useRef<Monaco | undefined>(undefined);

    useEffect(() => {
        const monaco: Monaco | undefined = monacoRef.current;
        if (!diffEditor || !monaco) {
            return;
        }
        const apply = (model: editor.ITextModel | null, markers: ContentMarker[] | undefined): void => {
            if (model) {
                monaco.editor.setModelMarkers(model, MARKER_OWNER, (markers || []).map(marker => ({
                    ...marker,
                    severity: monaco.MarkerSeverity.Error
                })));
            }
        };
        apply(diffEditor.getOriginalEditor().getModel(), originalMarkers);
        apply(diffEditor.getModifiedEditor().getModel(), modifiedMarkers);
    }, [diffEditor, originalMarkers, modifiedMarkers, original, modified]);

    useEffect(() => {
        if (diffEditor && revealModified) {
            diffEditor.getModifiedEditor().revealRangeInCenter(revealModified);
            diffEditor.getModifiedEditor().setSelection(revealModified);
        }
    }, [diffEditor, revealModified]);

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
                <RegistryDiffEditor
                    className="text-editor"
                    height={height}
                    original={original}
                    modified={modified}
                    options={diffEditorContentOptions}
                    onMount={(mountedEditor, monaco) => {
                        monacoRef.current = monaco;
                        setDiffEditor(mountedEditor);
                    }}
                />
            </div>
        </div>
    );
};
