import { FunctionComponent } from "react";
import { Link } from "react-router";
import {
    Card,
    CardBody,
    CardTitle,
    Spinner,
    Alert,
    Flex,
    FlexItem,
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant
} from "@patternfly/react-core";
import { InfoCircleIcon } from "@patternfly/react-icons";
import { FromNow } from "@apicurio/common-ui-components";
import { SearchedArtifact } from "@sdk/lib/generated-client/models";
import { ArtifactTypeIcon } from "@app/components";
import { AppNavigation, useAppNavigation } from "@services/useAppNavigation.ts";
import "./RecentArtifacts.css";

export type RecentArtifactsProps = {
    artifacts: SearchedArtifact[];
    isLoading: boolean;
    error: string | null;
};

export const RecentArtifacts: FunctionComponent<RecentArtifactsProps> = ({
    artifacts,
    isLoading,
    error
}: RecentArtifactsProps) => {
    const appNavigation: AppNavigation = useAppNavigation();

    const getArtifactLink = (artifact: SearchedArtifact): string => {
        const groupId = encodeURIComponent(artifact.groupId || "default");
        const artifactId = encodeURIComponent(artifact.artifactId!);
        return appNavigation.createLink(`/explore/${groupId}/${artifactId}`);
    };

    return (
        <Card className="recent-artifacts-card" variant="secondary" style={{ backgroundColor: "white" }}>
            <CardTitle>Recent Artifacts</CardTitle>
            <CardBody>
                {isLoading ? (
                    <div className="loading-container">
                        <Spinner size="lg" />
                    </div>
                ) : error ? (
                    <Alert variant="danger" title="Error loading artifacts" isInline>
                        {error}
                    </Alert>
                ) : artifacts.length === 0 ? (
                    <EmptyState headingLevel="h5" icon={InfoCircleIcon} titleText="No recent artifacts" variant={EmptyStateVariant.sm}>
                        <EmptyStateBody>
                            No recent artifacts found. Create your first artifact to get started.
                        </EmptyStateBody>
                    </EmptyState>
                ) : (
                    <div className="artifacts-list">
                        {artifacts.map((artifact, index) => (
                            <div key={`${artifact.groupId}-${artifact.artifactId}-${index}`} className="artifact-item">
                                <Flex alignItems={{ default: "alignItemsCenter" }}>
                                    <FlexItem className="artifact-icon">
                                        <ArtifactTypeIcon artifactType={artifact.artifactType!} />
                                    </FlexItem>
                                    <FlexItem className="artifact-info" grow={{ default: "grow" }}>
                                        <Link to={getArtifactLink(artifact)} className="artifact-name">
                                            {artifact.name || artifact.artifactId}
                                        </Link>
                                        <div className="artifact-meta">
                                            <span className="group-id">{artifact.groupId || "default"}</span>
                                            <span className="separator">/</span>
                                            <span className="artifact-id">{artifact.artifactId}</span>
                                        </div>
                                    </FlexItem>
                                    <FlexItem className="artifact-date">
                                        <FromNow date={artifact.modifiedOn || artifact.createdOn} />
                                    </FlexItem>
                                </Flex>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
};
