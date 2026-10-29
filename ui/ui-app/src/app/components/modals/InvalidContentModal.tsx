import React, { FunctionComponent } from "react";
import "./InvalidContentModal.css";
import {
    Button,
    DataList,
    DataListCell,
    DataListItemCells,
    DataListItemRow
} from "@patternfly/react-core";
import {
    Modal,
    ModalVariant
} from "@patternfly/react-core/deprecated";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { RuleViolationProblemDetails } from "@sdk/lib/generated-client/models";
import { CompatibilityReport } from "@app/components/ruleViolation";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { causesOf, isCompatibilityViolation, isRuleViolation, toViolationCause } from "@utils/ruleViolation.utils.ts";


/**
 * Properties
 */
export type InvalidContentModalProps = {
    error: RuleViolationProblemDetails|undefined;
    /** The content the user submitted, kept so it can be shown with the violations. */
    content?: string;
    contentType?: string;
    /** The artifact the content was submitted to, used to load the version it was compared against. */
    groupId?: string | null;
    artifactId?: string;
    isOpen: boolean;
    onClose: () => void;
};

/**
 * Models the "invalid content" modal.  This is shown when the user tries to upload content
 * that is not valid.
 */
export const InvalidContentModal: FunctionComponent<InvalidContentModalProps> = (props: InvalidContentModalProps) => {
    const groups: GroupsService = useGroupsService();

    const artifactId: string | undefined = props.artifactId;
    const loadComparedContent = artifactId === undefined ? undefined : (version: string): Promise<string> =>
        groups.getArtifactVersionContent(props.groupId ?? null, artifactId, version);

    // A report with content shows it next to the causes, in a modal that fills the viewport height.
    const isFullHeight: boolean = props.content !== undefined && isCompatibilityViolation(props.error);

    const errorDetail = (): React.ReactElement => {
        if (props.error) {
            if (isCompatibilityViolation(props.error)) {
                return (
                    <CompatibilityReport
                        causes={causesOf(props.error).map(toViolationCause)}
                        content={props.content}
                        contentType={props.contentType}
                        loadComparedContent={loadComparedContent}
                    />
                );
            } else if (isRuleViolation(props.error)) {
                return (
                    <DataList aria-label="Error causes" className="error-causes" >
                        {
                            causesOf(props.error).map( (cause, idx) =>
                                <DataListItemRow key={""+idx} className="error-causes-item">
                                    <DataListItemCells
                                        dataListCells={[
                                            <DataListCell key="error icon" className="type-icon-cell">
                                                <ExclamationCircleIcon/>
                                            </DataListCell>,
                                            <DataListCell key="main content">
                                                <div className="error-causes-item-title">
                                                    <span>{cause.context != null ? (<b>{cause.context}</b>) : cause.description}</span>
                                                </div>
                                                <div className="error-causes-item-description">{cause.context != null ? cause.description : cause.context }</div>
                                            </DataListCell>
                                        ]}
                                    />
                                </DataListItemRow>
                            )
                        }
                    </DataList>
                );
            } else if (props.error.detail) {
                return (
                    <pre className="error-detail">
                        {props.error.detail}
                    </pre>
                );
            }
        }
        return <p/>;
    };
    
    return (
        <Modal
            title="Invalid Content Error"
            variant={ModalVariant.large}
            isOpen={props.isOpen}
            onClose={props.onClose}
            className={`edit-artifact-metaData pf-m-redhat-font${isFullHeight ? " invalid-content-modal-report" : ""}`}
            actions={[
                <Button key="close" variant="link" data-testid="modal-btn-close" onClick={props.onClose}>Close</Button>
            ]}
        >
            <p className="modal-desc" >The content you attempted to upload violated one or more of the established content rules.</p>
            { errorDetail() }
        </Modal>
    );

};
