import { FunctionComponent } from "react";
import "./ArtifactInfoTabContent.css";
import "@app/styles/empty.css";
import { ArtifactTypeIcon, IfAuth, IfFeature, RuleList, RuleListType } from "@app/components";
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
import { PencilAltIcon } from "@patternfly/react-icons";
import { FromNow, If } from "@apicurio/common-ui-components";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import { ArtifactMetaData, Rule } from "@sdk/lib/generated-client/models";
import { labelsToAny } from "@utils/rest.utils.ts";

/**
 * Properties
 */
export type ArtifactInfoTabContentProps = {
    artifact: ArtifactMetaData;
    rules: Rule[];
    onEnableRule: (ruleType: string) => void;
    onDisableRule: (ruleType: string) => void;
    onConfigureRule: (ruleType: string, config: string) => void;
    onEditMetaData: () => void;
    onChangeOwner: () => void;
};

/**
 * Models the content of the Artifact Info tab.
 */
export const ArtifactInfoTabContent: FunctionComponent<ArtifactInfoTabContentProps> = (props: ArtifactInfoTabContentProps) => {

    const description = (): string => {
        return props.artifact.description || "No description";
    };

    const artifactName = (): string => {
        return props.artifact.name || "No name";
    };

    const labels: any = labelsToAny(props.artifact.labels);

    return (
        <div className="artifact-tab-content">
            <div className="artifact-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><ArtifactTypeIcon artifactType={props.artifact.artifactType!} /></FlexItem>
                                <FlexItem className="title">Artifact metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <IfAuth isDeveloper={true} owner={props.artifact.owner}>
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
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="artifact-details-description"
                                    className={!props.artifact.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="artifact-details-created-on">
                                    <FromNow date={props.artifact.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={!isStringEmptyOrUndefined(props.artifact.owner)}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="artifact-details-created-by">
                                        <span>{props.artifact.owner}</span>
                                        <span>
                                            <IfAuth isAdminOrOwner={true} owner={props.artifact.owner}>
                                                <IfFeature feature="readOnly" isNot={true}>
                                                    <Button id="edit-action"
                                                        data-testid="artifact-btn-change-owner"
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
                                <DescriptionListTerm>Labels</DescriptionListTerm>
                                {!labels || !Object.keys(labels).length ?
                                    <DescriptionListDescription data-testid="artifact-details-labels" className="empty-state-text">No labels</DescriptionListDescription> :
                                    <DescriptionListDescription data-testid="artifact-details-labels">{Object.entries(labels).map(([key, value]) =>
                                        <Label key={`label-${key}`} color="purple" style={{ marginBottom: "2px", marginRight: "5px" }}>
                                            <Truncate className="label-truncate" content={`${key}=${value}`} />
                                        </Label>
                                    )}</DescriptionListDescription>
                                }
                            </DescriptionListGroup>
                        </DescriptionList>
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
                        <RuleList
                            type={RuleListType.Artifact}
                            resourceOwner={props.artifact.owner}
                            rules={props.rules}
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
