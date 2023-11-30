import { FunctionComponent } from "react";
import "./ArtifactsPageEmptyState.css";
import {
    Button,
    EmptyState,
    EmptyStateActions,
    EmptyStateBody,
    EmptyStateFooter,
    EmptyStateIcon,
    EmptyStateVariant,
    Title
} from "@patternfly/react-core";
import { PlusCircleIcon } from "@patternfly/react-icons";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";

/**
 * Properties
 */
export type ArtifactsPageEmptyStateProps = {
    isFiltered: boolean;
    onUploadArtifact: () => void;
    onImportArtifacts: () => void;
};


/**
 * Models the empty state for the Artifacts page (when there are no artifacts).
 */
export const ArtifactsPageEmptyState: FunctionComponent<ArtifactsPageEmptyStateProps> = (props: ArtifactsPageEmptyStateProps) => {
    return (
        <EmptyState variant={EmptyStateVariant.full}>
            <EmptyStateIcon icon={PlusCircleIcon}/>
            <Title headingLevel="h5" size="lg">
                No artifacts found
            </Title>
            <If condition={() => props.isFiltered}>
                <EmptyStateBody>
                    No artifacts match your filter settings.  Change your filter or perhaps Upload a new
                    artifact.
                </EmptyStateBody>
            </If>
            <If condition={() => !props.isFiltered}>
                <EmptyStateBody>
                    There are currently no artifacts in the registry.  Upload artifacts to view them here.
                </EmptyStateBody>
            </If>
            <EmptyStateFooter>
                <EmptyStateActions>
                    <IfAuth isDeveloper={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="empty-btn-upload" variant="primary"
                                data-testid="empty-btn-upload" onClick={props.onUploadArtifact}>Upload artifact</Button>
                        </IfFeature>
                    </IfAuth>
                    <IfAuth isAdmin={true}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button className="empty-btn-import" variant="secondary"
                                data-testid="empty-btn-import" onClick={props.onImportArtifacts}>Upload multiple artifacts</Button>
                        </IfFeature>
                    </IfAuth>
                </EmptyStateActions>
            </EmptyStateFooter>
        </EmptyState>
    );

};
