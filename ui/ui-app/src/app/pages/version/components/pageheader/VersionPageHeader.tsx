import { FunctionComponent } from "react";
import "./VersionPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";


/**
 * Properties
 */
export type ArtifactVersionPageHeaderProps = {
    groupId: string;
    artifactId: string;
    version: string;
    onDelete: () => void;
    onDownload: () => void;
};

/**
 * Models the page header for the Artifact page.
 */
export const VersionPageHeader: FunctionComponent<ArtifactVersionPageHeaderProps> = (props: ArtifactVersionPageHeaderProps) => {
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
                        <span>{props.version}</span>
                    </Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <Button id="download-artifact-button" variant="primary"
                    data-testid="header-btn-download" onClick={props.onDownload}>Download</Button>
                <IfAuth isDeveloper={true}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <IfFeature feature="deleteVersion" is={true}>
                            <Button id="delete-artifact-button" variant="danger"
                                data-testid="header-btn-delete" onClick={props.onDelete}>Delete version</Button>
                        </IfFeature>
                    </IfFeature>
                </IfAuth>
            </FlexItem>
        </Flex>
    );
};
