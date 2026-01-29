import { FunctionComponent, useEffect, useState } from "react";
import "./VersionCompareModal.css";
import {
	Spinner,
	Alert
} from '@patternfly/react-core';
import {
	Modal
} from '@patternfly/react-core/deprecated';
import { SearchedVersion } from "@sdk/lib/generated-client/models";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { DiffView } from "@app/components";

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
    const [version1Content, setVersion1Content] = useState<string>("");
    const [version2Content, setVersion2Content] = useState<string>("");
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const groups: GroupsService = useGroupsService();

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
            <div className="compare-modal-content">
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
                    <DiffView
                        original={version1Content}
                        originalLabel={`Older: ${labels.older}`}
                        modified={version2Content}
                        modifiedLabel={`Newer: ${labels.newer}`}
                        originalAriaLabel="Older version"
                        modifiedAriaLabel="Newer version"
                    />
                )}
            </div>
        </Modal>
    );
};
