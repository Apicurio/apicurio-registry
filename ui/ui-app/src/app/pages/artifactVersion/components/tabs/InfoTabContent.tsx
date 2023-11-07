import { FunctionComponent } from "react";
import "./InfoTabContent.css";
import "@app/styles/empty.css";
import { ArtifactTypeIcon, If, IfAuth, IfFeature, RuleList } from "@app/components";
import {
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Flex,
    FlexItem,
    Label,
    Truncate
} from "@patternfly/react-core";
import { DownloadIcon, PencilAltIcon } from "@patternfly/react-icons";
import { ArtifactMetaData } from "@models/artifactMetaData.model.ts";
import { Rule } from "@models/rule.model.ts";
import { FromNow } from "@app/components/common/FromNow.tsx";

/**
 * Properties
 */
export type InfoTabContentProps = {
    artifact: ArtifactMetaData;
    isLatest: boolean;
    rules: Rule[];
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
    onDownloadArtifact: () => void;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
};

/**
 * Models the content of the Artifact Info tab.
 */
export const InfoTabContent: FunctionComponent<InfoTabContentProps> = (props: InfoTabContentProps) => {

    const labels = (): string[] => {
        return props.artifact.labels || [];
    };

    const description = (): string => {
        return props.artifact.description || "No description";
    };

    const artifactName = (): string => {
        return props.artifact.name || "No name";
    };

    return (
        <div className="artifact-tab-content">
            <div className="artifact-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><ArtifactTypeIcon type={props.artifact.type} /></FlexItem>
                                <FlexItem className="title">Version metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <IfAuth isDeveloper={true}>
                                        <IfFeature feature="readOnly" isNot={true}>
                                            <Button id="edit-action"
                                                data-testid="artifact-btn-edit"
                                                onClick={props.onEditMetaData}
                                                variant="link"><PencilAltIcon />{" "}Edit</Button>
                                        </IfFeature>
                                    </IfAuth>
                                </FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Name</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="artifact-details-name"
                                    className={!props.artifact.name ? "empty-state-text" : ""}
                                >
                                    { artifactName() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>ID</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-id">{props.artifact.id}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="artifact-details-description"
                                    className={!props.artifact.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Status</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-state">{props.artifact.state}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-created-on">
                                    <FromNow date={props.artifact.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={props.artifact.createdBy !== undefined && props.artifact.createdBy !== ""}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="artifact-details-created-by">
                                        <span>{props.artifact.createdBy}</span>
                                        <span>
                                            <IfAuth isAdminOrOwner={true} owner={props.artifact.createdBy}>
                                                <IfFeature feature="readOnly" isNot={true}>
                                                    <Button id="edit-action"
                                                        data-testid="artifact-btn-edit"
                                                        onClick={props.onChangeOwner}
                                                        variant="link"><PencilAltIcon /></Button>
                                                </IfFeature>
                                            </IfAuth>
                                        </span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-modified-on">
                                    <FromNow date={props.artifact.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Global ID</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-global-id">{props.artifact.globalId}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Content ID</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-content-id">{props.artifact.contentId}</DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {labels().length ?
                                    <DescriptionListDescription data-testid="artifact-details-labels">{
                                        labels().map((label) =>
                                            <Label key={`label-${label}`} color="blue" style={{ marginBottom: "2px", marginLeft: "5px" }}>
                                                <Truncate className="label-truncate" content={label} />
                                            </Label>
                                        )
                                    }</DescriptionListDescription> :
                                    <DescriptionListDescription className="empty-state-text" data-testid="artifact-details-labels">No labels</DescriptionListDescription>
                                }

                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Properties</DescriptionListTerm>
                                {!props.artifact.properties || !Object.keys(props.artifact.properties).length ?
                                    <DescriptionListDescription data-testid="artifact-details-properties" className="empty-state-text">No properties</DescriptionListDescription> :
                                    <DescriptionListDescription data-testid="artifact-details-properties">{Object.entries(props.artifact.properties).map(([key, value]) =>
                                        <Label key={`property-${key}`} color="purple" style={{ marginBottom: "2px", marginLeft: "5px" }}>
                                            <Truncate className="property-truncate" content={`${key}=${value}`} />
                                        </Label>
                                    )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
                        <div className="actions">
                            <Button id="download-action"
                                data-testid="artifact-btn-download"
                                title="Download artifact content"
                                onClick={props.onDownloadArtifact}
                                variant="secondary"><DownloadIcon /> Download</Button>
                        </div>
                    </CardBody>
                </Card>
            </div>
            <div className="artifact-rules">
                <Card>
                    <CardTitle>
                        <div className="rules-label">Artifact-specific rules</div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <p style={{ paddingBottom: "15px" }}>
                            Manage the content rules for this artifact. Each artifact-specific rule can be
                            individually enabled, configured, and disabled. Artifact-specific rules override
                            the equivalent global rules.
                        </p>
                        <RuleList rules={props.rules}
                            onEnableRule={props.onEnableRule}
                            onDisableRule={props.onDisableRule}
                            onConfigureRule={props.onConfigureRule}
                        />
                    </CardBody>
                </Card>
            </div>
        </div>
    );

};
