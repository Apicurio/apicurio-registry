import { FunctionComponent } from "react";
import "./BranchPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";
import { ArtifactMetaData, BranchMetaData } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type ArtifactBranchPageHeaderProps = {
    artifact: ArtifactMetaData | undefined;
    groupId: string;
    artifactId: string;
    branch: BranchMetaData;
    onDelete: () => void;
};

/**
 * Models the page header for the Branch page.
 */
export const BranchPageHeader: FunctionComponent<ArtifactBranchPageHeaderProps> = (props: ArtifactBranchPageHeaderProps) => {
    return (
        <Flex className="example-border">
            <FlexItem>
                <TextContent>
                    <Text component={TextVariants.h1}>
                        <If condition={props.groupId !== null && props.groupId !== undefined && props.groupId !== "default"}>
                            <span>{props.groupId}</span>
                            <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        </If>
                        <span>{props.artifactId}</span>
                        <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        <span>'{props.branch.branchId}' branch</span>
                    </Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <If condition={!(props.branch.systemDefined || false)}>
                    <IfAuth isDeveloper={true} owner={props.artifact?.owner}>
                        <IfFeature feature="readOnly" isNot={true}>
                            <Button id="delete-branch-button" variant="danger"
                                data-testid="header-btn-delete" onClick={props.onDelete}>Delete branch</Button>
                        </IfFeature>
                    </IfAuth>
                </If>
            </FlexItem>
        </Flex>
    );
};
