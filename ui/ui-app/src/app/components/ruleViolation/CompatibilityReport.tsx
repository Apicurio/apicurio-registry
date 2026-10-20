import { FunctionComponent, useEffect, useMemo, useRef, useState } from "react";
import "./CompatibilityReport.css";
import {
    Button,
    DataList,
    DataListCell,
    DataListItemCells,
    DataListItemRow,
    Label,
    Title
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import type { editor } from "monaco-editor";
import type { Monaco } from "@monaco-editor/react";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { DiffView } from "@app/components/common/DiffView.tsx";
import { draftContentToLanguage } from "@utils/content.utils.ts";
import { newLoaderGuard } from "@utils/loader.utils.ts";
import { buildContentMarkers, ContentIssue, ContentMarker } from "@utils/marker.utils.ts";
import {
    comparedVersionOf,
    groupCausesByLocation,
    ViolationCause,
    ViolationGroup
} from "@utils/ruleViolation.utils.ts";

/**
 * Properties
 */
export type CompatibilityReportProps = {
    causes: ViolationCause[];
    /** The content the user submitted, when the flow kept it. */
    content?: string;
    contentType?: string;
    /** Loads the content of the version the causes were compared against. */
    loadComparedContent?: (version: string) => Promise<string>;
};

/** The empty JSON Pointer is the whole document, which would otherwise render as a blank title. */
const locationTitle = (location: string): string => location === "" ? "(whole document)" : location;

const DIRECTION_LABELS: Record<string, string> = {
    BACKWARD: "Breaks backward compatibility",
    FORWARD: "Breaks forward compatibility",
    BOTH: "Breaks backward and forward compatibility"
};

const MARKER_OWNER: string = "apicurio-compatibility-report";

/**
 * Where a cause is in the submitted content: `proposed.pointer`, or `context` when the server sends no
 * location. A location in a referenced artifact is not in the submitted document, so it gets no highlight.
 */
const proposedIssueOf = (cause: ViolationCause): ContentIssue | undefined => {
    if (cause.proposed?.reference !== undefined) {
        return undefined;
    }
    const pointer: string | undefined = cause.proposed?.pointer ?? cause.context;
    return pointer === undefined ? undefined : { pointer, message: cause.description };
};

/** Where a cause is in the compared version, when the server reports it and it is not in a referenced artifact. */
const existingIssueOf = (cause: ViolationCause): ContentIssue | undefined => {
    if (cause.existing?.pointer === undefined || cause.existing.reference !== undefined) {
        return undefined;
    }
    return { pointer: cause.existing.pointer, message: cause.description };
};

const issuesOf = (causes: ViolationCause[], issueOf: (cause: ViolationCause) => ContentIssue | undefined): ContentIssue[] =>
    causes.map(issueOf).filter((issue): issue is ContentIssue => issue !== undefined);

const EDITOR_LINE_HEIGHT = 19;
const EDITOR_MAX_HEIGHT = 300;

/** Sizes an editor to the longest of its contents, up to a limit, so short content does not leave a large gap. */
const editorHeight = (...contents: string[]): string => {
    const lines: number = Math.max(...contents.map(content => content.split("\n").length));
    return `${Math.min(EDITOR_MAX_HEIGHT, (lines + 1) * EDITOR_LINE_HEIGHT)}px`;
};

/**
 * Shows the causes of a compatibility violation grouped by location, with the content that was submitted.
 * With only `description` and `context` on each cause it shows the same rows as the plain cause list.
 */
export const CompatibilityReport: FunctionComponent<CompatibilityReportProps> = (props: CompatibilityReportProps) => {
    const groups: ViolationGroup[] = groupCausesByLocation(props.causes);
    const comparedVersion: string | undefined = comparedVersionOf(props.causes);
    const language: string = draftContentToLanguage({ content: props.content ?? "", contentType: props.contentType ?? "" });

    const [isComparing, setIsComparing] = useState(false);
    const [comparedContent, setComparedContent] = useState<string>();
    const [compareError, setCompareError] = useState<string>();

    useEffect(() => {
        // Loaded once and kept: the causes, and so the compared version, do not change while this is shown.
        if (!isComparing || comparedContent !== undefined || comparedVersion === undefined || !props.loadComparedContent) {
            return;
        }
        const guard = newLoaderGuard();
        setCompareError(undefined);
        props.loadComparedContent(comparedVersion)
            .then(guard.wrap(setComparedContent))
            .catch(guard.wrap(() => setCompareError(`Version ${comparedVersion} could not be loaded.`)));
        return () => guard.cancel();
    }, [isComparing, comparedVersion]);

    const proposedMarkers: ContentMarker[] = useMemo(() => props.content === undefined ? [] :
        buildContentMarkers(props.content, issuesOf(props.causes, proposedIssueOf)).markers, [props.content, props.causes]);
    const existingMarkers: ContentMarker[] = useMemo(() => comparedContent === undefined ? [] :
        buildContentMarkers(comparedContent, issuesOf(props.causes, existingIssueOf)).markers, [comparedContent, props.causes]);
    // Where each group's location is in the submitted content, so its title can scroll there.
    const groupMarkers: (ContentMarker | undefined)[] = useMemo(() => groups.map(group => {
        const issue: ContentIssue | undefined = proposedIssueOf(group.causes[0]);
        return props.content === undefined || issue === undefined ? undefined :
            buildContentMarkers(props.content, [issue]).markers[0];
    }), [props.content, props.causes]);

    const [codeEditor, setCodeEditor] = useState<editor.IStandaloneCodeEditor>();
    const monacoRef = useRef<Monaco | undefined>(undefined);
    const [revealInComparison, setRevealInComparison] = useState<ContentMarker>();

    useEffect(() => {
        const model: editor.ITextModel | null | undefined = codeEditor?.getModel();
        const monaco: Monaco | undefined = monacoRef.current;
        if (!model || !monaco) {
            return;
        }
        monaco.editor.setModelMarkers(model, MARKER_OWNER, proposedMarkers.map(marker => ({
            ...marker,
            severity: monaco.MarkerSeverity.Error
        })));
    }, [codeEditor, proposedMarkers]);

    const isShowingComparison: boolean = isComparing && comparedContent !== undefined;

    const revealLocation = (marker: ContentMarker): void => {
        if (isShowingComparison) {
            // A new object each time, so a second click on the same location scrolls again.
            setRevealInComparison({ ...marker });
        } else {
            codeEditor?.revealRangeInCenter(marker);
            codeEditor?.setSelection(marker);
        }
    };
    const isLoadingComparison: boolean = isComparing && comparedContent === undefined && compareError === undefined;

    const toggleComparison = (): void => {
        setCompareError(undefined);
        setIsComparing(!isComparing);
    };

    const causeRows = (group: ViolationGroup) => group.causes
        .filter(cause => group.location !== undefined || cause.direction !== undefined)
        .map((cause, idx) => (
            <div key={idx} className="compatibility-report-cause" data-testid="compatibility-report-cause">
                {group.location !== undefined && <span>{cause.description}</span>}
                {cause.direction !== undefined && (
                    <Label isCompact={true} className="compatibility-report-direction">
                        {DIRECTION_LABELS[cause.direction] ?? `Breaks ${cause.direction}`}
                    </Label>
                )}
            </div>
        ));

    return (
        <div className="compatibility-report" data-testid="compatibility-report">
            <DataList aria-label="Compatibility violations by location" className="error-causes">
                {groups.map((group, idx) => (
                    <DataListItemRow key={idx} className="error-causes-item" data-testid="compatibility-report-group">
                        <DataListItemCells
                            dataListCells={[
                                <DataListCell key="error icon" className="type-icon-cell">
                                    <ExclamationCircleIcon />
                                </DataListCell>,
                                <DataListCell key="main content">
                                    <div className="error-causes-item-title">
                                        {group.location === undefined ? group.causes[0].description : groupMarkers[idx] !== undefined ? (
                                            <Button variant="link" isInline={true} data-testid="compatibility-report-location"
                                                onClick={() => revealLocation(groupMarkers[idx] as ContentMarker)}>
                                                <b>{locationTitle(group.location)}</b>
                                            </Button>
                                        ) : <b>{locationTitle(group.location)}</b>}
                                    </div>
                                    {group.reference !== undefined && (
                                        <div className="compatibility-report-reference">
                                            In referenced artifact <code>{group.reference}</code>
                                        </div>
                                    )}
                                    <div className="error-causes-item-description">{causeRows(group)}</div>
                                </DataListCell>
                            ]}
                        />
                    </DataListItemRow>
                ))}
            </DataList>
            {props.content !== undefined && (
                <div className="compatibility-report-content" data-testid="compatibility-report-content">
                    <div className="compatibility-report-content-header">
                        <Title headingLevel="h3" size="md">
                            {isShowingComparison ? `Submitted content compared with version ${comparedVersion}` : "Submitted content"}
                        </Title>
                        {comparedVersion !== undefined && props.loadComparedContent && (
                            <Button variant="link" isInline={true} data-testid="compatibility-report-compare"
                                onClick={toggleComparison}>
                                {isComparing ? "Show submitted content only" : `Compare with version ${comparedVersion}`}
                            </Button>
                        )}
                    </div>
                    {compareError !== undefined && <p className="compatibility-report-compare-error">{compareError}</p>}
                    {isLoadingComparison && (
                        <p className="compatibility-report-compare-loading" data-testid="compatibility-report-compare-loading">
                            Loading version {comparedVersion}...
                        </p>
                    )}
                    {isShowingComparison && comparedContent !== undefined ? (
                        <DiffView
                            original={comparedContent}
                            originalLabel={`Version ${comparedVersion}`}
                            modified={props.content}
                            modifiedLabel="Submitted"
                            height={editorHeight(comparedContent, props.content)}
                            originalMarkers={existingMarkers}
                            modifiedMarkers={proposedMarkers}
                            revealModified={revealInComparison}
                        />
                    ) : (
                        <RegistryCodeEditor
                            className="compatibility-report-editor"
                            height={editorHeight(props.content)}
                            language={language}
                            value={props.content}
                            onMount={(mountedEditor, monaco) => {
                                monacoRef.current = monaco;
                                setCodeEditor(mountedEditor);
                            }}
                            options={{
                                readOnly: true,
                                // Monaco hides markers in read-only editors unless told otherwise.
                                renderValidationDecorations: "on",
                                automaticLayout: true,
                                minimap: { enabled: false },
                                wordWrap: "on"
                            }}
                        />
                    )}
                </div>
            )}
        </div>
    );
};
