import { FunctionComponent } from "react";
import "./BranchInfoTabContent.css";
import "@app/styles/empty.css";
import { IfAuth, IfFeature } from "@app/components";
import {
    Alert,
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
    FlexItem
} from "@patternfly/react-core";
import { CodeBranchIcon, PencilAltIcon } from "@patternfly/react-icons";
import { FromNow, If } from "@apicurio/common-ui-components";
import { ArtifactMetaData, BranchMetaData } from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type BranchInfoTabContentProps = {
    artifact: ArtifactMetaData;
    branch: BranchMetaData;
    onEditMetaData: () => void;
};

/**
 * Models the content of the Branch Info (overview) tab.
 */
export const BranchInfoTabContent: FunctionComponent<BranchInfoTabContentProps> = (props: BranchInfoTabContentProps) => {

    const description = (): string => {
        return props.branch.description || "No description";
    };

    return (
        <div className="overview-tab-content">
            <div className="version-basics">
                <Card>
                    <CardTitle>
                        <div className="title-and-type">
                            <Flex>
                                <FlexItem className="type"><CodeBranchIcon /></FlexItem>
                                <FlexItem className="title">Branch metadata</FlexItem>
                                <FlexItem className="actions" align={{ default: "alignRight" }}>
                                    <If condition={!(props.branch.systemDefined || false)}>
                                        <IfAuth isDeveloper={true}>
                                            <IfFeature feature="readOnly" isNot={true}>
                                                <Button id="edit-action"
                                                    data-testid="version-btn-edit"
                                                    onClick={props.onEditMetaData}
                                                    variant="link"><PencilAltIcon />{" "}Edit</Button>
                                            </IfFeature>
                                        </IfAuth>
                                    </If>
                                </FlexItem>
                            </Flex>
                        </div>
                    </CardTitle>
                    <Divider />
                    <CardBody>
                        <DescriptionList className="metaData" isCompact={true}>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Description</DescriptionListTerm>
                                <DescriptionListDescription
                                    data-testid="version-details-description"
                                    className={!props.branch.description ? "empty-state-text" : ""}
                                >
                                    { description() }
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Created</DescriptionListTerm>
                                <DescriptionListDescription data-testid="version-details-created-on">
                                    <FromNow date={props.branch.createdOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            <If condition={props.branch.owner !== undefined && props.branch.owner !== ""}>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner</DescriptionListTerm>
                                    <DescriptionListDescription data-testid="version-details-created-by">
                                        <span>{props.branch.owner}</span>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            </If>
                            <DescriptionListGroup>
                                <DescriptionListTerm>Modified</DescriptionListTerm>
                                <DescriptionListDescription data-testid="version-details-modified-on">
                                    <FromNow date={props.artifact.modifiedOn} />
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                        </DescriptionList>
                        <If condition={props.branch.systemDefined || false}>
                            <Alert variant="info" title="Note: This branch was system generated" ouiaId="InfoAlert" />
                        </If>
                    </CardBody>
                </Card>
            </div>
        </div>
    );

};
