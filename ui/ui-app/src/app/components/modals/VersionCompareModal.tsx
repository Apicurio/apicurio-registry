import { FunctionComponent, useEffect, useState } from "react";
import "./VersionCompareModal.css";
import { Modal, ToggleGroup, ToggleGroupItem, Spinner, Alert } from "@patternfly/react-core";
import { editor } from "monaco-editor";
import { DiffEditor } from "@monaco-editor/react";
import { ArrowsAltHIcon } from "@patternfly/react-icons";
import IDiffEditorConstructionOptions = editor.IDiffEditorConstructionOptions;
import { SearchedVersion } from "@sdk/lib/generated-client/models";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";

/**
 * Properties
 */
export type VersionCompareModalProps = {
    isOpen: boolean;
    groupId: string | null;
    artifactId: string;
    version1: SearchedVersion | null;
    version2: SearchedVersion | null;
    onClose: () => void;
};

/**
 * A modal to compare two versions of an artifact side by side.
 */
export const VersionCompareModal: FunctionComponent<VersionCompareModalProps> = ({
    isOpen,
    groupId,
    artifactId,
    version1,
    version2,
    onClose
}: VersionCompareModalProps) => {
    const [diffEditorContentOptions, setDiffEditorContentOptions] = useState({
        renderSideBySide: true,
        automaticLayout: true,
        wordWrap: "off",
        readOnly: true,
        inDiffEditor: true,
        originalAriaLabel: "Older version",
        modifiedAriaLabel: "Newer version"
    } as IDiffEditorConstructionOptions);

    const [version1Content, setVersion1Content] = useState<string>("");
    const [version2Content, setVersion2Content] = useState<string>("");
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const [isDiffInline, setIsDiffInline] = useState(false);
    const [isDiffWrapped, setIsDiffWrapped] = useState(false);

    const groups: GroupsService = useGroupsService();

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

    // Load version contents when modal opens
    useEffect(() => {
        if (isOpen && version1 && version2) {
            setIsLoading(true);
            setError(null);

            Promise.all([
                groups.getArtifactVersionContent(groupId, artifactId, version1.version!),
                groups.getArtifactVersionContent(groupId, artifactId, version2.version!)
            ]).then(([content1, content2]) => {
                // Determine which version is older based on createdOn date
                const date1 = new Date(version1.createdOn!);
                const date2 = new Date(version2.createdOn!);

                if (date1 <= date2) {
                    setVersion1Content(content1);
                    setVersion2Content(content2);
                } else {
                    // Swap so older version is always on the left
                    setVersion1Content(content2);
                    setVersion2Content(content1);
                }
                setIsLoading(false);
            }).catch((err) => {
                console.error("Error loading version content:", err);
                setError("Failed to load version content. Please try again.");
                setIsLoading(false);
            });
        }
    }, [isOpen, version1, version2, groupId, artifactId]);

    // Reset state when modal closes
    useEffect(() => {
        if (!isOpen) {
            setVersion1Content("");
            setVersion2Content("");
            setError(null);
        }
    }, [isOpen]);

    const getVersionLabel = (version: SearchedVersion | null): string => {
        if (!version) return "";
        return version.name ? `${version.version} (${version.name})` : version.version!;
    };

    // Determine older and newer versions for labels
    const getOrderedVersionLabels = (): { older: string; newer: string } => {
        if (!version1 || !version2) return { older: "", newer: "" };

        const date1 = new Date(version1.createdOn!);
        const date2 = new Date(version2.createdOn!);

        if (date1 <= date2) {
            return {
                older: getVersionLabel(version1),
                newer: getVersionLabel(version2)
            };
        } else {
            return {
                older: getVersionLabel(version2),
                newer: getVersionLabel(version1)
            };
        }
    };

    const labels = getOrderedVersionLabels();

    return (
        <Modal
            id="version-compare-modal"
            title="Compare Versions"
            isOpen={isOpen}
            onClose={onClose}
            aria-label="Compare artifact versions"
        >
            <div className="compare-view">
                {isLoading ? (
                    <div className="compare-loading">
                        <Spinner size="lg" />
                        <span>Loading version content...</span>
                    </div>
                ) : error ? (
                    <Alert variant="danger" title="Error" isInline>
                        {error}
                    </Alert>
                ) : (
                    <>
                        <ToggleGroup className="compare-toggle-group" aria-label="Compare view toggle group">
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
                        <div className="compare-label">
                            <span className="before">Older: {labels.older}</span>
                            <span className="divider">
                                <ArrowsAltHIcon />
                            </span>
                            <span className="after">Newer: {labels.newer}</span>
                        </div>
                        <div className="compare-editor">
                            <DiffEditor
                                className="text-editor"
                                original={version1Content}
                                modified={version2Content}
                                options={diffEditorContentOptions}
                            />
                        </div>
                    </>
                )}
            </div>
        </Modal>
    );
};
