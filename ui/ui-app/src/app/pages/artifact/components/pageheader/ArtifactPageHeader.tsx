import { FunctionComponent } from "react";
import "./ArtifactPageHeader.css";
import { Button, Flex, FlexItem, Text, TextContent, TextVariants } from "@patternfly/react-core";
import { IfAuth, IfFeature } from "@app/components";
import { If } from "@apicurio/common-ui-components";
import { ArtifactMetaData } from "@sdk/lib/generated-client/models";


/**
 * Properties
 */
export type ArtifactPageHeaderProps = {
    artifact: ArtifactMetaData;
    onDeleteArtifact: () => void;
};

/**
 * Models the page header for the Artifact page.
 */
export const ArtifactPageHeader: FunctionComponent<ArtifactPageHeaderProps> = (props: ArtifactPageHeaderProps) => {
    return (
        <Flex className="example-border">
            <FlexItem>
                <TextContent>
                    <Text component={TextVariants.h1}>
                        <If condition={props.artifact.groupId !== null && props.artifact.groupId !== undefined && props.artifact.groupId !== "default"}>
                            <span>{props.artifact.groupId}</span>
                            <span style={{ color: "#6c6c6c", marginLeft: "10px", marginRight: "10px" }}> / </span>
                        </If>
                        <span>{props.artifact.artifactId}</span>
                    </Text>
                </TextContent>
            </FlexItem>
            <FlexItem align={{ default: "alignRight" }}>
                <IfAuth isDeveloper={true}>
                    <IfFeature feature="readOnly" isNot={true}>
                        <Button id="delete-artifact-button" variant="danger"
                            data-testid="header-btn-delete" onClick={props.onDeleteArtifact}>Delete artifact</Button>
                    </IfFeature>
                </IfAuth>
            </FlexItem>
        </Flex>
    );
};
